package com.sme.maintenance.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.AuthenticatedUserService;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    // NOTE: In-memory registry is single-instance only; does not scale horizontally across multiple instances without a shared broker (e.g. Redis Pub/Sub).
    private final Map<UUID, Set<String>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    private final JwtDecoder jwtDecoder;
    private final AuthenticatedUserService authenticatedUserService;
    private final RoomService roomService;
    private final com.sme.maintenance.presence.PresenceService presenceService;
    private final ObjectMapper objectMapper;

    private static final int CARD_MOVE_RATE_LIMIT = 15;
    private static final int CURSOR_MOVE_RATE_LIMIT = 25;
    private static final int STROKE_POINTS_RATE_LIMIT = 20;
    private static final int DRAWING_MOVE_RATE_LIMIT = 15;
    private static final long AUTH_TIMEOUT_MS = 5000;
    private static final long HEARTBEAT_INTERVAL_MS = 20000;
    private static final long SESSION_IDLE_TIMEOUT_MS = 30000;

    public record ObjectLock(
            UUID roomId,
            String objectType,
            String objectId,
            Long userId,
            String userName,
            Instant expiresAt
    ) {}

    private final Map<String, ObjectLock> heldObjects = new ConcurrentHashMap<>();

    private static final String[] PALETTE = new String[] {
            "#3b82f6", "#ec4899", "#10b981", "#f59e0b", "#8b5cf6", "#14b8a6", "#ef4444", "#6366f1"
    };

    public static String getDeterministicColor(Long userId) {
        if (userId == null) return "#3b82f6";
        int idx = (int) (Math.abs(userId) % PALETTE.length);
        return PALETTE[idx];
    }

    public RoomWebSocketHandler(
            JwtDecoder jwtDecoder,
            AuthenticatedUserService authenticatedUserService,
            RoomService roomService,
            com.sme.maintenance.presence.PresenceService presenceService,
            ObjectMapper objectMapper) {
        this.jwtDecoder = jwtDecoder;
        this.authenticatedUserService = authenticatedUserService;
        this.roomService = roomService;
        this.presenceService = presenceService;
        this.objectMapper = objectMapper;
    }

    private static class SessionContext {
        final WebSocketSession session;
        final Instant connectedAt;
        volatile boolean authenticated;
        volatile UUID roomId;
        volatile Long userId;
        volatile String userName;
        volatile Instant lastActive;
        volatile Instant lastPingSent;

        // Rate limiter state: card-move (15/s), cursor-move (25/s), stroke-points (20/s), drawing-move (15/s)
        volatile long cardMoveSec;
        final AtomicInteger cardMoveCount = new AtomicInteger(0);
        volatile long cursorMoveSec;
        final AtomicInteger cursorMoveCount = new AtomicInteger(0);
        volatile long strokePointsSec;
        final AtomicInteger strokePointsCount = new AtomicInteger(0);
        volatile long drawingMoveSec;
        final AtomicInteger drawingMoveCount = new AtomicInteger(0);

        SessionContext(WebSocketSession session) {
            this.session = session;
            this.connectedAt = Instant.now();
            this.lastActive = Instant.now();
            this.lastPingSent = Instant.now();
            long sec = Instant.now().getEpochSecond();
            this.cardMoveSec = sec;
            this.cursorMoveSec = sec;
            this.strokePointsSec = sec;
            this.drawingMoveSec = sec;
        }

        boolean allowCardMove() {
            long currentSec = Instant.now().getEpochSecond();
            if (currentSec != cardMoveSec) {
                cardMoveSec = currentSec;
                cardMoveCount.set(1);
                return true;
            }
            return cardMoveCount.incrementAndGet() <= CARD_MOVE_RATE_LIMIT;
        }

        boolean allowCursorMove() {
            long currentSec = Instant.now().getEpochSecond();
            if (currentSec != cursorMoveSec) {
                cursorMoveSec = currentSec;
                cursorMoveCount.set(1);
                return true;
            }
            return cursorMoveCount.incrementAndGet() <= CURSOR_MOVE_RATE_LIMIT;
        }

        boolean allowStrokePoints() {
            long currentSec = Instant.now().getEpochSecond();
            if (currentSec != strokePointsSec) {
                strokePointsSec = currentSec;
                strokePointsCount.set(1);
                return true;
            }
            return strokePointsCount.incrementAndGet() <= STROKE_POINTS_RATE_LIMIT;
        }

        boolean allowDrawingMove() {
            long currentSec = Instant.now().getEpochSecond();
            if (currentSec != drawingMoveSec) {
                drawingMoveSec = currentSec;
                drawingMoveCount.set(1);
                return true;
            }
            return drawingMoveCount.incrementAndGet() <= DRAWING_MOVE_RATE_LIMIT;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
        SessionContext context = new SessionContext(concurrentSession);
        sessions.put(session.getId(), context);

        String roomIdStr = (String) session.getAttributes().get("roomId");
        if (roomIdStr == null || roomIdStr.isBlank()) {
            concurrentSession.close(new CloseStatus(4400, "Missing room ID"));
            return;
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdStr);
            context.roomId = roomId;
        } catch (IllegalArgumentException e) {
            concurrentSession.close(new CloseStatus(4400, "Invalid room ID"));
            return;
        }

        // Check if queryToken was passed during handshake
        String queryToken = (String) session.getAttributes().get("queryToken");
        if (queryToken != null && !queryToken.isBlank()) {
            boolean success = authenticateAndJoin(context, queryToken);
            if (!success) {
                // authenticateAndJoin closed session with code
                return;
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionContext context = sessions.get(session.getId());
        if (context == null) {
            return;
        }

        context.lastActive = Instant.now();
        String payload = message.getPayload();
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.debug("Invalid JSON received from session {}", session.getId());
            return;
        }

        if (!context.authenticated) {
            String type = root.hasNonNull("type") ? root.get("type").asText() : "";
            if ("auth".equalsIgnoreCase(type) && root.hasNonNull("token")) {
                String token = root.get("token").asText();
                authenticateAndJoin(context, token);
            } else {
                context.session.close(new CloseStatus(4401, "Expected auth message"));
            }
            return;
        }

        String type = root.hasNonNull("type") ? root.get("type").asText() : "";
        if ("card-move".equalsIgnoreCase(type)) {
            handleCardMove(context, root);
        } else if ("cursor-move".equalsIgnoreCase(type)) {
            handleCursorMove(context, root);
        } else if ("cursor-leave".equalsIgnoreCase(type)) {
            handleCursorLeave(context);
        } else if ("stroke-points".equalsIgnoreCase(type)) {
            handleStrokePoints(context, root);
        } else if ("drawing-move".equalsIgnoreCase(type)) {
            handleDrawingMove(context, root);
        } else if ("object-unlock".equalsIgnoreCase(type)) {
            handleObjectUnlock(context, root);
        } else if ("pong".equalsIgnoreCase(type)) {
            context.lastActive = Instant.now();
        }
    }

    private void handleCardMove(SessionContext context, JsonNode root) {
        if (!context.allowCardMove()) {
            log.debug("Rate limit exceeded for session {} on card-move", context.session.getId());
            return; // drop excess
        }

        String cardId = root.hasNonNull("id") ? root.get("id").asText() : null;
        if (cardId == null || cardId.isBlank()) {
            return;
        }

        if (!root.hasNonNull("x") || !root.hasNonNull("y")) {
            return;
        }

        double x = root.get("x").asDouble();
        double y = root.get("y").asDouble();

        // Canvas bounds validation (0..1000 x 0..500)
        if (x < 0 || x > 1000 || y < 0 || y > 500) {
            log.debug("Dropped card-move out of bounds: x={}, y={}", x, y);
            return;
        }

        // Soft lock check: reject if held by another user
        if (!tryAcquireOrRenewLock(context.roomId, "card", cardId, context)) {
            log.debug("card-move dropped: card {} is locked by another user", cardId);
            return;
        }

        // Sender identity comes from token context (NEVER from client payload)
        Map<String, Object> relay = Map.of(
                "type", "card-move",
                "id", cardId,
                "x", x,
                "y", y,
                "by", context.userId != null ? context.userId : "anonymous"
        );

        broadcastToRoom(context.roomId, relay, context.session.getId());
    }

    private void handleCursorMove(SessionContext context, JsonNode root) {
        if (!context.allowCursorMove()) {
            log.debug("Rate limit exceeded for session {} on cursor-move", context.session.getId());
            return; // drop excess
        }

        if (!root.hasNonNull("x") || !root.hasNonNull("y")) {
            return;
        }

        double x = root.get("x").asDouble();
        double y = root.get("y").asDouble();

        // Canvas bounds validation (0..1000 x 0..500)
        if (x < 0 || x > 1000 || y < 0 || y > 500) {
            log.debug("Dropped cursor-move out of bounds: x={}, y={}", x, y);
            return;
        }

        // Sender identity injected from session context (never trust client)
        Map<String, Object> relay = Map.of(
                "type", "cursor-move",
                "userId", context.userId != null ? context.userId : 0L,
                "name", context.userName != null ? context.userName : "Anonymous",
                "color", getDeterministicColor(context.userId),
                "x", x,
                "y", y
        );

        broadcastToRoom(context.roomId, relay, context.session.getId());
    }

    private void handleCursorLeave(SessionContext context) {
        if (context.roomId == null || context.userId == null) {
            return;
        }
        Map<String, Object> relay = Map.of(
                "type", "cursor-leave",
                "userId", context.userId
        );
        broadcastToRoom(context.roomId, relay, context.session.getId());
    }

    private void handleDrawingMove(SessionContext context, JsonNode root) {
        if (!context.allowDrawingMove()) {
            log.debug("Rate limit exceeded for session {} on drawing-move", context.session.getId());
            return;
        }

        String drawingId = root.hasNonNull("id") ? root.get("id").asText() : null;
        if (drawingId == null || drawingId.isBlank()) {
            return;
        }

        if (!root.hasNonNull("offsetX") || !root.hasNonNull("offsetY")) {
            return;
        }

        double offsetX = root.get("offsetX").asDouble();
        double offsetY = root.get("offsetY").asDouble();

        if (offsetX < -2000 || offsetX > 2000 || offsetY < -2000 || offsetY > 2000) {
            log.debug("Dropped drawing-move out of bounds: offsetX={}, offsetY={}", offsetX, offsetY);
            return;
        }

        if (!tryAcquireOrRenewLock(context.roomId, "drawing", drawingId, context)) {
            log.debug("drawing-move dropped: drawing {} is locked by another user", drawingId);
            return;
        }

        Map<String, Object> relay = Map.of(
                "type", "drawing-move",
                "id", drawingId,
                "offsetX", offsetX,
                "offsetY", offsetY,
                "by", context.userId != null ? context.userId : "anonymous"
        );

        broadcastToRoom(context.roomId, relay, context.session.getId());
    }

    private void handleStrokePoints(SessionContext context, JsonNode root) {
        if (!context.allowStrokePoints()) {
            log.debug("Rate limit exceeded for session {} on stroke-points", context.session.getId());
            return;
        }

        String strokeId = root.hasNonNull("strokeId") ? root.get("strokeId").asText() : null;
        if (strokeId == null || strokeId.isBlank()) {
            return;
        }

        String color = root.hasNonNull("color") ? root.get("color").asText() : "#273b3b";
        double width = root.hasNonNull("width") ? root.get("width").asDouble() : 5.0;
        double opacity = root.hasNonNull("opacity") ? root.get("opacity").asDouble() : 1.0;
        JsonNode pointsNode = root.get("points");
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.isEmpty()) {
            return;
        }

        List<Object> pointsList = new ArrayList<>();
        int count = 0;
        for (JsonNode pt : pointsNode) {
            if (++count > 100) break;
            if (pt.isArray() && pt.size() >= 2) {
                pointsList.add(List.of(pt.get(0).asDouble(), pt.get(1).asDouble()));
            } else if (pt.isObject() && pt.hasNonNull("x") && pt.hasNonNull("y")) {
                pointsList.add(Map.of("x", pt.get("x").asDouble(), "y", pt.get("y").asDouble()));
            }
        }

        Map<String, Object> relay = Map.of(
                "type", "stroke-points",
                "strokeId", strokeId,
                "userId", context.userId != null ? context.userId : 0L,
                "color", color,
                "width", width,
                "opacity", opacity,
                "points", pointsList
        );

        broadcastToRoom(context.roomId, relay, context.session.getId());
    }

    private void handleObjectUnlock(SessionContext context, JsonNode root) {
        String objectType = root.hasNonNull("objectType") ? root.get("objectType").asText() : "";
        String id = root.hasNonNull("id") ? root.get("id").asText() : "";
        if (id.isBlank() || (!"card".equals(objectType) && !"drawing".equals(objectType))) {
            return;
        }
        unlockObject(context.roomId, objectType, id, context.userId);
    }

    private synchronized boolean tryAcquireOrRenewLock(UUID roomId, String objectType, String id, SessionContext context) {
        String key = roomId + ":" + objectType + ":" + id;
        Instant now = Instant.now();
        ObjectLock current = heldObjects.get(key);
        if (current != null && current.expiresAt().isAfter(now)) {
            if (!current.userId().equals(context.userId)) {
                return false;
            }
        }
        boolean isNew = (current == null || !current.expiresAt().isAfter(now));
        ObjectLock newLock = new ObjectLock(roomId, objectType, id, context.userId, context.userName, now.plusMillis(3000));
        heldObjects.put(key, newLock);
        if (isNew) {
            broadcastToRoom(roomId, Map.of(
                    "type", "object-locked",
                    "objectType", objectType,
                    "id", id,
                    "userId", context.userId != null ? context.userId : 0L,
                    "name", context.userName != null ? context.userName : "User"
            ), null);
        }
        return true;
    }

    private synchronized void unlockObject(UUID roomId, String objectType, String id, Long userId) {
        String key = roomId + ":" + objectType + ":" + id;
        ObjectLock current = heldObjects.get(key);
        if (current != null && current.userId().equals(userId)) {
            heldObjects.remove(key);
            broadcastToRoom(roomId, Map.of(
                    "type", "object-unlocked",
                    "objectType", objectType,
                    "id", id
            ), null);
        }
    }

    private synchronized void releaseLocksForUser(UUID roomId, Long userId) {
        if (roomId == null || userId == null) return;
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ObjectLock> entry : heldObjects.entrySet()) {
            ObjectLock lock = entry.getValue();
            if (lock.roomId().equals(roomId) && lock.userId().equals(userId)) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            ObjectLock lock = heldObjects.remove(key);
            if (lock != null) {
                broadcastToRoom(roomId, Map.of(
                        "type", "object-unlocked",
                        "objectType", lock.objectType(),
                        "id", lock.objectId()
                ), null);
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public synchronized void sweepExpiredLocks() {
        Instant now = Instant.now();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ObjectLock> entry : heldObjects.entrySet()) {
            if (!entry.getValue().expiresAt().isAfter(now)) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            ObjectLock lock = heldObjects.remove(key);
            if (lock != null) {
                broadcastToRoom(lock.roomId(), Map.of(
                        "type", "object-unlocked",
                        "objectType", lock.objectType(),
                        "id", lock.objectId()
                ), null);
            }
        }
    }

    private synchronized boolean authenticateAndJoin(SessionContext context, String token) throws IOException {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Token decoding failed for session {}: {}", context.session.getId(), e.getMessage());
            context.session.close(new CloseStatus(4401, "Invalid token"));
            return false;
        }

        if (jwt.getExpiresAt() != null && Instant.now().isAfter(jwt.getExpiresAt())) {
            log.warn("Expired token presented for session {}", context.session.getId());
            context.session.close(new CloseStatus(4401, "Expired token"));
            return false;
        }

        User user;
        try {
            user = authenticatedUserService.getCurrentUser(jwt);
        } catch (Exception e) {
            log.warn("User resolution failed for session {}: {}", context.session.getId(), e.getMessage());
            context.session.close(new CloseStatus(4401, "Authentication failed"));
            return false;
        }

        // Check room access rules
        com.sme.maintenance.room.Room room;
        try {
            room = roomService.memberRoom(context.roomId, user.getId());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Room {} not found for user {}", context.roomId, user.getId());
                context.session.close(new CloseStatus(4404, "Room not found"));
            } else {
                log.warn("Forbidden room access {} for user {}", context.roomId, user.getId());
                context.session.close(new CloseStatus(4403, "Forbidden"));
            }
            return false;
        }

        context.authenticated = true;
        context.userId = user.getId();
        context.userName = user.getUsername() != null ? user.getUsername() : ("User #" + user.getId());

        roomSessions.computeIfAbsent(context.roomId, k -> ConcurrentHashMap.newKeySet()).add(context.session.getId());

        if (presenceService != null) {
            boolean isOwner = room != null && room.getOwnerId() != null && room.getOwnerId().equals(user.getId());
            presenceService.heartbeat(context.roomId, user.getId(), context.session.getId(), isOwner);
        }

        // Send auth success acknowledgement
        Map<String, Object> ack = Map.of("type", "auth.success", "userId", context.userId, "name", context.userName);
        context.session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));

        log.info("Session {} authenticated for user {} in room {}", context.session.getId(), context.userId, context.roomId);

        // Broadcast updated presence to everyone in the room
        broadcastPresence(context.roomId);
        return true;
    }

    private void broadcastPresence(UUID roomId) {
        if (roomId == null) return;
        Set<String> sessionIds = roomSessions.get(roomId);
        if (sessionIds == null || sessionIds.isEmpty()) return;

        Map<Long, Map<String, Object>> uniqueParticipants = new ConcurrentHashMap<>();
        for (String sId : sessionIds) {
            SessionContext ctx = sessions.get(sId);
            if (ctx != null && ctx.authenticated && ctx.userId != null) {
                uniqueParticipants.put(ctx.userId, Map.of(
                        "userId", ctx.userId,
                        "name", ctx.userName != null ? ctx.userName : ("User #" + ctx.userId)
                ));
            }
        }

        List<Map<String, Object>> participantsList = new ArrayList<>(uniqueParticipants.values());
        Map<String, Object> presenceMsg = Map.of(
                "type", "presence",
                "participants", participantsList
        );

        broadcastToRoom(roomId, presenceMsg, null);
    }

    public void broadcastToRoom(UUID roomId, Map<String, Object> payload, String excludeSessionId) {
        Set<String> sessionIds = roomSessions.get(roomId);
        if (sessionIds == null || sessionIds.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket message", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (String sId : sessionIds) {
            if (excludeSessionId != null && excludeSessionId.equals(sId)) {
                continue;
            }
            SessionContext ctx = sessions.get(sId);
            if (ctx != null && ctx.session.isOpen()) {
                try {
                    ctx.session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.debug("Failed to send message to session {}: {}", sId, e.getMessage());
                }
            }
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        SessionContext ctx = sessions.get(session.getId());
        if (ctx != null) {
            ctx.lastActive = Instant.now();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionContext context = sessions.remove(session.getId());
        if (context != null && context.roomId != null) {
            Set<String> rSessions = roomSessions.get(context.roomId);
            if (rSessions != null) {
                rSessions.remove(session.getId());
                if (rSessions.isEmpty()) {
                    roomSessions.remove(context.roomId);
                }
            }
            if (context.authenticated) {
                log.info("Session {} closed for user {} in room {}", session.getId(), context.userId, context.roomId);
                if (context.userId != null) {
                    if (presenceService != null) {
                        try {
                            com.sme.maintenance.room.Room r = roomService.find(context.roomId);
                            boolean isOwner = r != null && context.userId.equals(r.getOwnerId());
                            presenceService.leave(context.roomId, context.userId, session.getId(), isOwner);
                        } catch (Exception ignored) {}
                    }
                    releaseLocksForUser(context.roomId, context.userId);
                    Map<String, Object> leave = Map.of(
                            "type", "cursor-leave",
                            "userId", context.userId
                    );
                    broadcastToRoom(context.roomId, leave, session.getId());
                }
                broadcastPresence(context.roomId);
            }
        }
    }

    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
    public void runHeartbeat() {
        Instant now = Instant.now();

        for (Map.Entry<String, SessionContext> entry : sessions.entrySet()) {
            SessionContext ctx = entry.getValue();
            WebSocketSession session = ctx.session;

            if (!session.isOpen()) {
                sessions.remove(entry.getKey());
                continue;
            }

            // Close unauthenticated sessions after timeout
            if (!ctx.authenticated) {
                if (Duration.between(ctx.connectedAt, now).toMillis() > AUTH_TIMEOUT_MS) {
                    try {
                        session.close(new CloseStatus(4401, "Auth timeout"));
                    } catch (IOException ignored) {}
                    sessions.remove(entry.getKey());
                }
                continue;
            }

            // Prune dead sessions inactive for > 30s
            if (Duration.between(ctx.lastActive, now).toMillis() > SESSION_IDLE_TIMEOUT_MS) {
                try {
                    log.info("Closing idle session {}", session.getId());
                    session.close(new CloseStatus(4408, "Ping timeout"));
                } catch (IOException ignored) {}
                sessions.remove(entry.getKey());
                if (ctx.roomId != null) {
                    Set<String> rSessions = roomSessions.get(ctx.roomId);
                    if (rSessions != null) {
                        rSessions.remove(session.getId());
                    }
                    if (ctx.userId != null) {
                        if (presenceService != null) {
                            try {
                                com.sme.maintenance.room.Room r = roomService.find(ctx.roomId);
                                boolean isOwner = r != null && ctx.userId.equals(r.getOwnerId());
                                presenceService.leave(ctx.roomId, ctx.userId, session.getId(), isOwner);
                            } catch (Exception ignored) {}
                        }
                        releaseLocksForUser(ctx.roomId, ctx.userId);
                        Map<String, Object> leave = Map.of(
                                "type", "cursor-leave",
                                "userId", ctx.userId
                        );
                        broadcastToRoom(ctx.roomId, leave, session.getId());
                    }
                    broadcastPresence(ctx.roomId);
                }
                continue;
            }

            // Send ping frame
            try {
                session.sendMessage(new PingMessage(ByteBuffer.wrap(new byte[0])));
                ctx.lastPingSent = now;
            } catch (Exception ex) {
                log.debug("Failed to send ping to session {}: {}", session.getId(), ex.getMessage());
            }
        }
    }
}
