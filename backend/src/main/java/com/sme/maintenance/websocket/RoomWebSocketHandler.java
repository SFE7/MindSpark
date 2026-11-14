package com.sme.maintenance.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
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
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.services.AuthenticatedUserService;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final JwtDecoder jwtDecoder;
    private final AuthenticatedUserService authenticatedUserService;
    private final RoomRepository roomRepository;
    private final PresenceService presenceService;
    private final RoomBroadcastService broadcastService;
    private final ObjectMapper objectMapper;
    private Clock clock;

    private final long authTimeoutMs;
    private final long pingIntervalMs;
    private final long pongTimeoutMs;
    private final long tokenExpiryGraceMs;
    private final int maxTextMessageSize;
    private final int maxUnauthenticatedSessions;
    private final int maxSessionsPerUserPerRoom;

    public static class SessionMetadata {
        private final WebSocketSession session;
        private final Instant connectedAt;
        private volatile boolean authenticated;
        private volatile UUID roomId;
        private volatile Long userId;
        private volatile String clientId;
        private volatile boolean isOwner;
        private volatile Instant tokenExpiresAt;
        private volatile Instant lastPingSent;
        private volatile Instant lastPongReceived;

        public SessionMetadata(WebSocketSession session, Instant now) {
            this.session = session;
            this.connectedAt = now;
            this.authenticated = false;
            this.lastPingSent = now;
            this.lastPongReceived = now;
        }

        public WebSocketSession getSession() { return session; }
        public Instant getConnectedAt() { return connectedAt; }
        public boolean isAuthenticated() { return authenticated; }
        public UUID getRoomId() { return roomId; }
        public Long getUserId() { return userId; }
        public String getClientId() { return clientId; }
        public boolean isOwner() { return isOwner; }
        public Instant getTokenExpiresAt() { return tokenExpiresAt; }
        public Instant getLastPongReceived() { return lastPongReceived; }

        public void setAuthenticated(UUID roomId, Long userId, String clientId, boolean isOwner, Instant tokenExpiresAt, Instant now) {
            this.authenticated = true;
            this.roomId = roomId;
            this.userId = userId;
            this.clientId = clientId;
            this.isOwner = isOwner;
            this.tokenExpiresAt = tokenExpiresAt;
            this.lastPongReceived = now;
        }

        public void updateToken(Instant tokenExpiresAt) {
            this.tokenExpiresAt = tokenExpiresAt;
        }

        public void recordPing(Instant now) {
            this.lastPingSent = now;
        }

        public void recordPong(Instant now) {
            this.lastPongReceived = now;
        }
    }

    private final ConcurrentHashMap<String, SessionMetadata> sessions = new ConcurrentHashMap<>();

    @Autowired
    public RoomWebSocketHandler(
            JwtDecoder jwtDecoder,
            AuthenticatedUserService authenticatedUserService,
            RoomRepository roomRepository,
            PresenceService presenceService,
            RoomBroadcastService broadcastService,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) Clock clock,
            @Value("${mindspark.websocket.auth-timeout-ms:5000}") long authTimeoutMs,
            @Value("${mindspark.websocket.ping-interval-ms:5000}") long pingIntervalMs,
            @Value("${mindspark.websocket.pong-timeout-ms:10000}") long pongTimeoutMs,
            @Value("${mindspark.websocket.token-expiry-grace-ms:30000}") long tokenExpiryGraceMs,
            @Value("${mindspark.websocket.max-text-message-size-bytes:8192}") int maxTextMessageSize,
            @Value("${mindspark.websocket.max-unauthenticated-sessions:100}") int maxUnauthenticatedSessions,
            @Value("${mindspark.websocket.max-sessions-per-user-per-room:10}") int maxSessionsPerUserPerRoom) {
        this.jwtDecoder = jwtDecoder;
        this.authenticatedUserService = authenticatedUserService;
        this.roomRepository = roomRepository;
        this.presenceService = presenceService;
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.authTimeoutMs = authTimeoutMs;
        this.pingIntervalMs = pingIntervalMs;
        this.pongTimeoutMs = pongTimeoutMs;
        this.tokenExpiryGraceMs = tokenExpiryGraceMs;
        this.maxTextMessageSize = maxTextMessageSize;
        this.maxUnauthenticatedSessions = maxUnauthenticatedSessions;
        this.maxSessionsPerUserPerRoom = maxSessionsPerUserPerRoom;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Clock getClock() {
        return clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        int unauthCount = 0;
        for (SessionMetadata meta : sessions.values()) {
            if (!meta.isAuthenticated()) {
                unauthCount++;
            }
        }
        if (unauthCount >= maxUnauthenticatedSessions) {
            log.warn("Exceeded max unauthenticated sessions ({}), rejecting new connection", maxUnauthenticatedSessions);
            session.close(new CloseStatus(4000, "Too many unauthenticated connections"));
            return;
        }

        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, 10000, 512 * 1024);
        decorated.setTextMessageSizeLimit(maxTextMessageSize);

        Instant now = clock.instant();
        SessionMetadata meta = new SessionMetadata(decorated, now);
        sessions.put(session.getId(), meta);
        log.info("New WebSocket connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > maxTextMessageSize) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }

        SessionMetadata meta = sessions.get(session.getId());
        if (meta == null) {
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String type = root.hasNonNull("type") ? root.get("type").asText() : "";

        if (!meta.isAuthenticated()) {
            // Before auth, accept ONLY the auth message (Amendment 6)
            if (!"auth".equals(type)) {
                log.warn("Unauthenticated session {} sent message type {}, closing", session.getId(), type);
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            handleAuthMessage(session, meta, root);
        } else {
            // Re-auth / token refresh while already authenticated (Amendment 3)
            if ("auth".equals(type)) {
                handleTokenRefreshMessage(session, meta, root);
            }
        }
    }

    private void handleAuthMessage(WebSocketSession session, SessionMetadata meta, JsonNode root) throws IOException {
        String token = root.hasNonNull("token") ? root.get("token").asText() : null;
        String roomIdStr = root.hasNonNull("roomId") ? root.get("roomId").asText() : null;
        String clientId = root.hasNonNull("clientId") ? root.get("clientId").asText() : null;

        if (token == null || token.isBlank() || roomIdStr == null || roomIdStr.isBlank() || clientId == null || clientId.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdStr);
        } catch (IllegalArgumentException e) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("WebSocket auth failed: invalid JWT");
            session.close(new CloseStatus(4401, "Invalid token"));
            return;
        }

        Instant tokenExpiresAt = jwt.getExpiresAt();
        User user = authenticatedUserService.getCurrentUser(jwt);
        if (user == null) {
            session.close(new CloseStatus(4401, "User not found"));
            return;
        }

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            session.close(new CloseStatus(4404, "Room not found"));
            return;
        }

        boolean isOwner = room.getOwnerId().equals(user.getId());
        boolean isMember = isOwner || room.getMemberIds().contains(user.getId());
        if (!isMember) {
            log.warn("User {} is not a member of room {}, closing 4403", user.getId(), roomId);
            session.close(new CloseStatus(4403, "Forbidden: not a room member"));
            return;
        }

        // Cap sockets per user per room (Amendment 6)
        if (broadcastService.getSessionCount(roomId, user.getId()) >= maxSessionsPerUserPerRoom) {
            RoomBroadcastService.SocketBinding oldest = broadcastService.getOldestSession(roomId, user.getId());
            if (oldest != null && oldest.session().isOpen()) {
                oldest.session().close(new CloseStatus(4429, "Too many connections"));
            }
        }

        Instant now = clock.instant();
        meta.setAuthenticated(roomId, user.getId(), clientId, isOwner, tokenExpiresAt, now);
        broadcastService.registerSession(roomId, user.getId(), clientId, meta.getSession());
        presenceService.recordSocketOpen(roomId, user.getId(), clientId, isOwner);

        // Send auth confirmation with current sequence
        long currentSeq = broadcastService.getCurrentSequence(roomId);
        String ackPayload = objectMapper.writeValueAsString(Map.of(
                "type", "auth.success",
                "sequence", currentSeq
        ));
        meta.getSession().sendMessage(new TextMessage(ackPayload));

        if (isOwner) {
            long seq = broadcastService.nextSequence(roomId);
            broadcastService.broadcast(roomId, RoomEvent.roomOffline(roomId, seq, now, false));
        }

        log.info("WebSocket session {} authenticated for user {} in room {}", session.getId(), user.getId(), roomId);
    }

    private void handleTokenRefreshMessage(WebSocketSession session, SessionMetadata meta, JsonNode root) throws IOException {
        String token = root.hasNonNull("token") ? root.get("token").asText() : null;
        if (token == null || token.isBlank()) {
            return;
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Token refresh rejected: invalid token");
            return;
        }

        User user = authenticatedUserService.getCurrentUser(jwt);
        if (user == null || !Objects.equals(user.getId(), meta.getUserId())) {
            session.close(new CloseStatus(4401, "Token user mismatch"));
            return;
        }

        meta.updateToken(jwt.getExpiresAt());
        String ack = objectMapper.writeValueAsString(Map.of("type", "auth.success"));
        meta.getSession().sendMessage(new TextMessage(ack));
        log.info("Refreshed token on WebSocket session {} for user {}", session.getId(), meta.getUserId());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        SessionMetadata meta = sessions.get(session.getId());
        if (meta != null && meta.isAuthenticated()) {
            Instant now = clock.instant();
            meta.recordPong(now);
            presenceService.recordSocketPong(meta.getRoomId(), meta.getUserId(), meta.getClientId(), meta.isOwner());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionMetadata meta = sessions.remove(session.getId());
        if (meta != null && meta.isAuthenticated()) {
            broadcastService.unregisterSession(meta.getRoomId(), meta.getSession());
            presenceService.recordSocketClose(meta.getRoomId(), meta.getUserId(), meta.getClientId(), meta.isOwner());
            log.info("WebSocket session {} closed for user {} in room {} with status {}",
                    session.getId(), meta.getUserId(), meta.getRoomId(), status);
        } else {
            log.info("Unauthenticated WebSocket session {} closed with status {}", session.getId(), status);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void runMaintenance() {
        Instant now = clock.instant();

        for (Map.Entry<String, SessionMetadata> entry : sessions.entrySet()) {
            SessionMetadata meta = entry.getValue();
            WebSocketSession session = meta.getSession();

            if (!session.isOpen()) {
                sessions.remove(entry.getKey());
                continue;
            }

            // 1. Auth timeout check (5s)
            if (!meta.isAuthenticated()) {
                if (Duration.between(meta.getConnectedAt(), now).toMillis() > authTimeoutMs) {
                    try {
                        log.warn("Session {} timed out waiting for auth message, closing", session.getId());
                        session.close(CloseStatus.POLICY_VIOLATION);
                    } catch (IOException ignored) {}
                    sessions.remove(entry.getKey());
                }
                continue;
            }

            // 2. Token expiry check with 30s grace (Amendment 3)
            if (meta.getTokenExpiresAt() != null) {
                Instant deadline = meta.getTokenExpiresAt().plusMillis(tokenExpiryGraceMs);
                if (now.isAfter(deadline)) {
                    try {
                        log.warn("Token expired on session {} for user {}, closing 4401", session.getId(), meta.getUserId());
                        session.close(new CloseStatus(4401, "Token expired"));
                    } catch (IOException ignored) {}
                    sessions.remove(entry.getKey());
                    continue;
                }
            }

            // 3. Pong timeout check (10s)
            if (Duration.between(meta.getLastPongReceived(), now).toMillis() > pongTimeoutMs) {
                try {
                    log.warn("Pong timeout on session {} for user {}, closing", session.getId(), meta.getUserId());
                    session.close(new CloseStatus(4408, "Ping timeout"));
                } catch (IOException ignored) {}
                sessions.remove(entry.getKey());
                continue;
            }

            // 4. Send ping frames (5s)
            if (Duration.between(meta.lastPingSent, now).toMillis() >= pingIntervalMs) {
                try {
                    session.sendMessage(new PingMessage(ByteBuffer.wrap(new byte[0])));
                    meta.recordPing(now);
                } catch (Exception ex) {
                    log.warn("Failed to send ping on session {}: {}", session.getId(), ex.getMessage());
                }
            }
        }
    }
}
