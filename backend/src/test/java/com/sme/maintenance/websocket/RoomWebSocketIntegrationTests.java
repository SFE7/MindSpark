package com.sme.maintenance.websocket;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaService;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.UserService;
import com.sme.maintenance.vote.VoteService;
import com.sme.maintenance.comment.CommentService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(RoomWebSocketIntegrationTests.TestJwtConfig.class)
class RoomWebSocketIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private IdeaService ideaService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private com.sme.maintenance.drawing.DrawingService drawingService;

    @Autowired
    private com.sme.maintenance.drawing.DrawingRepository drawingRepository;

    @Autowired
    private RoomWebSocketHandler webSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private User userC;
    private User nonMember;

    private Room roomA;
    private Room roomB;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> {
                if ("invalid-token".equals(token)) {
                    throw new BadJwtException("Invalid token");
                }
                if ("expired-token".equals(token)) {
                    return Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject("expired-sub")
                            .claim("email", "expired@example.com")
                            .claim("preferred_username", "expired")
                            .expiresAt(Instant.now().minus(Duration.ofHours(1)))
                            .build();
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token)
                        .claim("email", token + "@example.com")
                        .claim("preferred_username", token)
                        .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                        .build();
            };
        }
    }

    @BeforeEach
    void setUp() {
        userA = userService.getOrCreateUser("user-a", "user-a@example.com", "userA");
        userB = userService.getOrCreateUser("user-b", "user-b@example.com", "userB");
        userC = userService.getOrCreateUser("user-c", "user-c@example.com", "userC");
        nonMember = userService.getOrCreateUser("non-member", "non-member@example.com", "nonMember");

        roomA = roomService.create("Room A", userA.getId());
        roomB = roomService.create("Room B", userC.getId());

        // Add userB to roomA
        roomA.getMemberIds().add(userB.getId());
        roomA = roomRepository.save(roomA);
    }

    @AfterEach
    void tearDown() {
        try {
            drawingRepository.deleteAll();
            if (roomA != null && roomA.getId() != null) roomRepository.deleteById(roomA.getId());
            if (roomB != null && roomB.getId() != null) roomRepository.deleteById(roomB.getId());
        } catch (Exception ignored) {}
    }

    private WebSocketSession connectClient(UUID roomId, String token, BlockingQueue<String> queue) throws Exception {
        return connectClient(roomId, token, null, queue);
    }

    private WebSocketSession connectClient(UUID roomId, String token, String origin, BlockingQueue<String> queue) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        String uri = "ws://localhost:" + port + "/ws/rooms/" + roomId + (token != null ? "?token=" + token : "");
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null && !origin.isBlank()) {
            headers.setOrigin(origin);
        }
        return client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                queue.add(message.getPayload());
            }
        }, headers, URI.create(uri)).get(5, TimeUnit.SECONDS);
    }

    @Test
    void cardMoveAndPresenceAreIsolatedBetweenRooms() throws Exception {
        BlockingQueue<String> msgsA1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsB = new LinkedBlockingQueue<>();

        // Connect userA to roomA
        WebSocketSession sA1 = connectClient(roomA.getId(), "user-a", msgsA1);
        assertThat(sA1.isOpen()).isTrue();

        // Connect userB to roomA
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);
        assertThat(sA2.isOpen()).isTrue();

        // Connect userC to roomB
        WebSocketSession sB = connectClient(roomB.getId(), "user-c", msgsB);
        assertThat(sB.isOpen()).isTrue();

        // Wait for presence on A1
        String presA1 = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA1.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"")) {
                presA1 = m;
                if (m.contains("user-a") && m.contains("user-b")) {
                    break;
                }
            }
        }
        assertThat(presA1).isNotNull();
        assertThat(presA1).contains("user-a");
        assertThat(presA1).contains("user-b");

        // Now userA sends card-move in Room A
        String cardMoveJson = objectMapper.writeValueAsString(Map.of(
                "type", "card-move",
                "id", "card-123",
                "x", 200,
                "y", 150
        ));
        sA1.sendMessage(new TextMessage(cardMoveJson));

        // Session A2 in Room A must receive the card-move
        String receivedByA2 = msgsA2.poll(3, TimeUnit.SECONDS);
        while (receivedByA2 != null && !receivedByA2.contains("card-move")) {
            receivedByA2 = msgsA2.poll(2, TimeUnit.SECONDS);
        }
        assertThat(receivedByA2).isNotNull();
        JsonNode a2Node = objectMapper.readTree(receivedByA2);
        assertThat(a2Node.get("type").asText()).isEqualTo("card-move");
        assertThat(a2Node.get("id").asText()).isEqualTo("card-123");
        assertThat(a2Node.get("x").asDouble()).isEqualTo(200.0);
        assertThat(a2Node.get("y").asDouble()).isEqualTo(150.0);
        assertThat(a2Node.get("by").asLong()).isEqualTo(userA.getId());

        // Session A1 (the sender) must NOT receive the card-move
        String echoedToA1 = msgsA1.poll(500, TimeUnit.MILLISECONDS);
        assertThat(echoedToA1 == null || !echoedToA1.contains("card-move")).isTrue();

        // Session B (in Room B) must NOT receive Room A's card move
        String receivedByB = msgsB.poll(500, TimeUnit.MILLISECONDS);
        assertThat(receivedByB == null || !receivedByB.contains("card-123")).isTrue();

        // Now disconnect Session A2 and verify presence updates on A1
        sA2.close();
        String presenceAfterLeave = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA1.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"") && !m.contains("user-b")) {
                presenceAfterLeave = m;
                break;
            }
        }
        assertThat(presenceAfterLeave).isNotNull();
        assertThat(presenceAfterLeave).contains("user-a");
        assertThat(presenceAfterLeave).doesNotContain("user-b");

        sA1.close();
        sB.close();
    }

    @Test
    void unauthenticatedOrUnauthorizedConnectionIsRejected() throws Exception {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        // 1. Invalid token
        WebSocketSession invalidSession = connectClient(roomA.getId(), "invalid-token", queue);
        Thread.sleep(500);
        assertThat(invalidSession.isOpen()).isFalse();

        // 2. Expired token
        WebSocketSession expiredSession = connectClient(roomA.getId(), "expired-token", queue);
        Thread.sleep(500);
        assertThat(expiredSession.isOpen()).isFalse();

        // 3. User is not a member of room
        WebSocketSession forbiddenSession = connectClient(roomA.getId(), "non-member", queue);
        Thread.sleep(500);
        assertThat(forbiddenSession.isOpen()).isFalse();
    }

    @Test
    void cursorMoveAndLeaveRelayedWithIdentityAndBoundsCheck() throws Exception {
        BlockingQueue<String> msgsA1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsB = new LinkedBlockingQueue<>();

        WebSocketSession sA1 = connectClient(roomA.getId(), "user-a", msgsA1);
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);
        WebSocketSession sB = connectClient(roomB.getId(), "user-c", msgsB);

        // Wait until both A1 and A2 are authenticated and registered in roomA
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"") && m.contains("user-a") && m.contains("user-b")) {
                break;
            }
        }
        msgsA2.clear();

        // A1 sends valid cursor-move
        String moveJson = objectMapper.writeValueAsString(Map.of(
                "type", "cursor-move",
                "x", 350.5,
                "y", 220.0
        ));
        sA1.sendMessage(new TextMessage(moveJson));

        // Session A2 receives cursor-move with injected userId, name, and deterministic color
        String receivedByA2 = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"cursor-move\"")) {
                receivedByA2 = m;
                break;
            }
        }
        assertThat(receivedByA2).isNotNull();
        JsonNode node = objectMapper.readTree(receivedByA2);
        assertThat(node.get("type").asText()).isEqualTo("cursor-move");
        assertThat(node.get("userId").asLong()).isEqualTo(userA.getId());
        assertThat(node.get("name").asText()).isEqualTo("user-a");
        assertThat(node.get("color").asText()).isEqualTo(RoomWebSocketHandler.getDeterministicColor(userA.getId()));
        assertThat(node.get("x").asDouble()).isEqualTo(350.5);
        assertThat(node.get("y").asDouble()).isEqualTo(220.0);

        // Sender A1 does NOT receive echoed cursor-move
        String echoedA1 = msgsA1.poll(500, TimeUnit.MILLISECONDS);
        assertThat(echoedA1 == null || !echoedA1.contains("\"cursor-move\"")).isTrue();

        // Session B does NOT receive Room A's cursor move
        String recvB = msgsB.poll(500, TimeUnit.MILLISECONDS);
        assertThat(recvB == null || !recvB.contains("\"cursor-move\"")).isTrue();

        // A1 sends out-of-bounds cursor-move (x = 1050, y = -10) -> must be dropped
        String outOfBounds = objectMapper.writeValueAsString(Map.of(
                "type", "cursor-move",
                "x", 1050,
                "y", -10
        ));
        sA1.sendMessage(new TextMessage(outOfBounds));
        String droppedMsg = msgsA2.poll(500, TimeUnit.MILLISECONDS);
        assertThat(droppedMsg == null || !droppedMsg.contains("1050")).isTrue();

        // A1 sends cursor-leave
        sA1.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "cursor-leave"))));
        String leaveReceived = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"cursor-leave\"")) {
                leaveReceived = m;
                break;
            }
        }
        assertThat(leaveReceived).isNotNull();
        JsonNode leaveNode = objectMapper.readTree(leaveReceived);
        assertThat(leaveNode.get("type").asText()).isEqualTo("cursor-leave");
        assertThat(leaveNode.get("userId").asLong()).isEqualTo(userA.getId());

        sA1.close();
        sA2.close();
        sB.close();
    }

    @Test
    void cursorMoveRateLimitingDropsExcessWithoutClosingConnection() throws Exception {
        BlockingQueue<String> msgsA1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();

        WebSocketSession sA1 = connectClient(roomA.getId(), "user-a", msgsA1);
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);

        // Clear any welcome/presence messages
        Thread.sleep(300);
        msgsA2.clear();

        // Send 35 cursor-move messages in rapid succession
        for (int i = 0; i < 35; i++) {
            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "cursor-move",
                    "x", 100 + i,
                    "y", 100
            ));
            sA1.sendMessage(new TextMessage(msg));
        }

        // Connection must remain OPEN
        Thread.sleep(500);
        assertThat(sA1.isOpen()).isTrue();
        assertThat(sA2.isOpen()).isTrue();

        // Count received cursor-move messages on sA2
        int count = 0;
        String m;
        while ((m = msgsA2.poll(200, TimeUnit.MILLISECONDS)) != null) {
            if (m.contains("\"cursor-move\"")) {
                count++;
            }
        }

        // Must drop excess messages without closing connection
        assertThat(count).isLessThan(35);
        assertThat(count).isGreaterThan(0);

        sA1.close();
        sA2.close();
    }

    @Test
    void ideaAndVoteAndCommentChangesBroadcastAfterCommit() throws Exception {
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);

        // Wait for connection and presence
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"")) {
                break;
            }
        }
        msgsA2.clear();

        // 1. Create idea via REST service
        Idea created = ideaService.create(roomA.getId(), "Test Idea Content", userA.getId());
        assertThat(created).isNotNull();

        String ideaCreatedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"idea-created\"")) {
                ideaCreatedMsg = m;
                break;
            }
        }
        assertThat(ideaCreatedMsg).isNotNull();
        JsonNode createNode = objectMapper.readTree(ideaCreatedMsg);
        assertThat(createNode.get("type").asText()).isEqualTo("idea-created");
        assertThat(createNode.get("idea").get("id").asText()).isEqualTo(created.getId().toString());
        assertThat(createNode.get("idea").get("title").asText()).isEqualTo("Test Idea Content");
        assertThat(createNode.get("version").asLong()).isGreaterThan(0L);

        // 2. Vote on idea via REST service
        voteService.add(created.getId(), userB.getId());

        String ideaVotedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"idea-updated\"")) {
                ideaVotedMsg = m;
                break;
            }
        }
        assertThat(ideaVotedMsg).isNotNull();
        JsonNode voteNode = objectMapper.readTree(ideaVotedMsg);
        assertThat(voteNode.get("type").asText()).isEqualTo("idea-updated");
        assertThat(voteNode.get("idea").get("id").asText()).isEqualTo(created.getId().toString());
        assertThat(voteNode.get("idea").get("voteCount").asInt()).isEqualTo(1);

        // 3. Add comment on idea via REST service
        commentService.addComment(created.getId(), userA.getId(), "Great brainstorm!");

        String commentCreatedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"comment-created\"")) {
                commentCreatedMsg = m;
                break;
            }
        }
        assertThat(commentCreatedMsg).isNotNull();
        JsonNode commentNode = objectMapper.readTree(commentCreatedMsg);
        assertThat(commentNode.get("type").asText()).isEqualTo("comment-created");
        assertThat(commentNode.get("comment").get("content").asText()).isEqualTo("Great brainstorm!");

        // 4. Delete idea via REST service
        ideaService.delete(created.getId(), userA.getId());

        String ideaDeletedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"idea-deleted\"")) {
                ideaDeletedMsg = m;
                break;
            }
        }
        assertThat(ideaDeletedMsg).isNotNull();
        JsonNode deleteNode = objectMapper.readTree(ideaDeletedMsg);
        assertThat(deleteNode.get("type").asText()).isEqualTo("idea-deleted");
        assertThat(deleteNode.get("id").asText()).isEqualTo(created.getId().toString());

        sA2.close();
    }

    @Test
    void strokePointsRelayedToOtherSessions() throws Exception {
        BlockingQueue<String> msgsA1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();

        WebSocketSession sA1 = connectClient(roomA.getId(), "user-a", msgsA1);
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);

        for (int i = 0; i < 10; i++) {
            String m = msgsA2.poll(500, TimeUnit.MILLISECONDS);
            if (m != null && m.contains("\"presence\"") && m.contains("user-b")) {
                break;
            }
        }

        UUID strokeId = UUID.randomUUID();
        String strokeJson = objectMapper.writeValueAsString(Map.of(
                "type", "stroke-points",
                "strokeId", strokeId.toString(),
                "color", "#273b3b",
                "width", 5.0,
                "opacity", 1.0,
                "points", java.util.List.of(java.util.List.of(10.0, 20.0), java.util.List.of(15.0, 25.0))
        ));

        msgsA1.clear();
        sA1.sendMessage(new TextMessage(strokeJson));

        String received = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"stroke-points\"")) {
                received = m;
                break;
            }
        }
        assertThat(received).isNotNull();
        JsonNode node = objectMapper.readTree(received);
        assertThat(node.get("type").asText()).isEqualTo("stroke-points");
        assertThat(node.get("strokeId").asText()).isEqualTo(strokeId.toString());
        assertThat(node.get("userId").asLong()).isEqualTo(userA.getId());
        assertThat(node.get("points").size()).isEqualTo(2);

        // Sender sA1 must not receive its own stroke-points
        assertThat(msgsA1.poll(300, TimeUnit.MILLISECONDS)).isNull();

        sA1.close();
        sA2.close();
    }

    @Test
    void drawingMoveRelayedWithSoftLockAndUnlock() throws Exception {
        BlockingQueue<String> msgsA1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();

        WebSocketSession sA1 = connectClient(roomA.getId(), "user-a", msgsA1);
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);

        for (int i = 0; i < 10; i++) {
            String m = msgsA2.poll(500, TimeUnit.MILLISECONDS);
            if (m != null && m.contains("\"presence\"") && m.contains("user-b")) {
                break;
            }
        }

        UUID drawingId = UUID.randomUUID();

        // 1. User A moves drawing -> should acquire lock and relay move
        String moveJson = objectMapper.writeValueAsString(Map.of(
                "type", "drawing-move",
                "id", drawingId.toString(),
                "offsetX", 25.0,
                "offsetY", 15.0
        ));
        sA1.sendMessage(new TextMessage(moveJson));

        // sA2 should receive object-locked and drawing-move
        boolean gotLocked = false;
        boolean gotMove = false;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null) {
                if (m.contains("\"object-locked\"")) gotLocked = true;
                if (m.contains("\"drawing-move\"")) gotMove = true;
                if (gotLocked && gotMove) break;
            }
        }
        assertThat(gotLocked).isTrue();
        assertThat(gotMove).isTrue();

        // 2. User B tries to move the same drawing while locked by user A -> dropped!
        String userBMove = objectMapper.writeValueAsString(Map.of(
                "type", "drawing-move",
                "id", drawingId.toString(),
                "offsetX", 99.0,
                "offsetY", 99.0
        ));
        sA2.sendMessage(new TextMessage(userBMove));

        // sA1 must NOT receive drawing-move with offsetX 99
        String leakedMove = null;
        for (int i = 0; i < 3; i++) {
            String m = msgsA1.poll(500, TimeUnit.MILLISECONDS);
            if (m != null && m.contains("\"drawing-move\"") && m.contains("99")) {
                leakedMove = m;
                break;
            }
        }
        assertThat(leakedMove).isNull();

        // 3. User A releases lock with object-unlock
        String unlockJson = objectMapper.writeValueAsString(Map.of(
                "type", "object-unlock",
                "objectType", "drawing",
                "id", drawingId.toString()
        ));
        sA1.sendMessage(new TextMessage(unlockJson));

        String unlockedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"object-unlocked\"")) {
                unlockedMsg = m;
                break;
            }
        }
        assertThat(unlockedMsg).isNotNull();
        JsonNode unlockNode = objectMapper.readTree(unlockedMsg);
        assertThat(unlockNode.get("type").asText()).isEqualTo("object-unlocked");
        assertThat(unlockNode.get("id").asText()).isEqualTo(drawingId.toString());

        sA1.close();
        sA2.close();
    }

    @Test
    void drawingChangesBroadcastAfterCommit() throws Exception {
        BlockingQueue<String> msgsA2 = new LinkedBlockingQueue<>();
        WebSocketSession sA2 = connectClient(roomA.getId(), "user-b", msgsA2);

        // 1. Create drawing via REST service
        com.sme.maintenance.api.ApiModels.DrawingResponse created = drawingService.createDrawing(
                roomA.getId(),
                userA.getId(),
                new com.sme.maintenance.api.ApiModels.DrawingRequest(
                        null,
                        "pen",
                        "#273b3b",
                        5.0,
                        1.0,
                        "[[1,2],[3,4]]",
                        0.0,
                        0.0
                )
        );

        String drawingCreatedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"drawing-created\"")) {
                drawingCreatedMsg = m;
                break;
            }
        }
        assertThat(drawingCreatedMsg).isNotNull();
        JsonNode createNode = objectMapper.readTree(drawingCreatedMsg);
        assertThat(createNode.get("type").asText()).isEqualTo("drawing-created");
        assertThat(createNode.get("drawing").get("id").asText()).isEqualTo(created.id().toString());
        assertThat(createNode.get("drawing").get("kind").asText()).isEqualTo("pen");
        assertThat(createNode.get("version").asLong()).isGreaterThan(0L);

        // 2. Move drawing via REST service
        drawingService.moveDrawing(
                roomA.getId(),
                created.id(),
                userA.getId(),
                new com.sme.maintenance.api.ApiModels.DrawingMoveRequest(12.0, 34.0)
        );

        String drawingUpdatedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"drawing-updated\"")) {
                drawingUpdatedMsg = m;
                break;
            }
        }
        assertThat(drawingUpdatedMsg).isNotNull();
        JsonNode updateNode = objectMapper.readTree(drawingUpdatedMsg);
        assertThat(updateNode.get("type").asText()).isEqualTo("drawing-updated");
        assertThat(updateNode.get("drawing").get("offsetX").asDouble()).isEqualTo(12.0);

        // 3. Delete drawing via REST service
        drawingService.deleteDrawing(roomA.getId(), created.id(), userA.getId());

        String drawingDeletedMsg = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA2.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"drawing-deleted\"")) {
                drawingDeletedMsg = m;
                break;
            }
        }
        assertThat(drawingDeletedMsg).isNotNull();
        JsonNode deleteNode = objectMapper.readTree(drawingDeletedMsg);
        assertThat(deleteNode.get("type").asText()).isEqualTo("drawing-deleted");
        assertThat(deleteNode.get("id").asText()).isEqualTo(created.id().toString());

        sA2.close();
    }

    @Test
    void multiClientWebSocketCollaborationOverLanOrigin() throws Exception {
        BlockingQueue<String> msgsA = new LinkedBlockingQueue<>();
        BlockingQueue<String> msgsB = new LinkedBlockingQueue<>();
        String lanOrigin = "http://192.168.0.3:4200";

        // 1. User A connects with LAN Origin
        WebSocketSession sA = connectClient(roomA.getId(), "user-a", lanOrigin, msgsA);
        assertThat(sA.isOpen()).isTrue();

        // User A receives initial presence snapshot showing 1 participant
        String presA1 = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"") && m.contains("user-a")) {
                presA1 = m;
                break;
            }
        }
        assertThat(presA1).isNotNull();
        JsonNode pres1Node = objectMapper.readTree(presA1);
        assertThat(pres1Node.get("participants").size()).isEqualTo(1);

        // 2. User B connects with LAN Origin to the same room
        WebSocketSession sB = connectClient(roomA.getId(), "user-b", lanOrigin, msgsB);
        assertThat(sB.isOpen()).isTrue();

        // Both clients receive presence snapshot showing 2 online participants (participants.length == 2)
        String presA2 = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"") && m.contains("user-b")) {
                presA2 = m;
                break;
            }
        }
        assertThat(presA2).isNotNull();
        JsonNode a2Node = objectMapper.readTree(presA2);
        assertThat(a2Node.get("participants").size()).isEqualTo(2);

        String presB2 = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsB.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"") && m.contains("user-a") && m.contains("user-b")) {
                presB2 = m;
                break;
            }
        }
        assertThat(presB2).isNotNull();
        JsonNode b2Node = objectMapper.readTree(presB2);
        assertThat(b2Node.get("participants").size()).isEqualTo(2);

        // 3. User A sends live cursor move -> User B receives it
        msgsB.clear();
        String cursorJson = objectMapper.writeValueAsString(Map.of(
                "type", "cursor-move",
                "x", 450.0,
                "y", 250.0
        ));
        sA.sendMessage(new TextMessage(cursorJson));

        String recvCursor = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsB.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"cursor-move\"")) {
                recvCursor = m;
                break;
            }
        }
        assertThat(recvCursor).isNotNull();
        JsonNode cursorNode = objectMapper.readTree(recvCursor);
        assertThat(cursorNode.get("type").asText()).isEqualTo("cursor-move");
        assertThat(cursorNode.get("userId").asLong()).isEqualTo(userA.getId());
        assertThat(cursorNode.get("x").asDouble()).isEqualTo(450.0);
        assertThat(cursorNode.get("y").asDouble()).isEqualTo(250.0);

        // 4. User A drags an idea -> User B receives card-move
        msgsB.clear();
        String cardMoveJson = objectMapper.writeValueAsString(Map.of(
                "type", "card-move",
                "id", "card-test-77",
                "x", 320.0,
                "y", 180.0
        ));
        sA.sendMessage(new TextMessage(cardMoveJson));

        String recvCardMove = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsB.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"card-move\"")) {
                recvCardMove = m;
                break;
            }
        }
        assertThat(recvCardMove).isNotNull();
        JsonNode cardNode = objectMapper.readTree(recvCardMove);
        assertThat(cardNode.get("type").asText()).isEqualTo("card-move");
        assertThat(cardNode.get("id").asText()).isEqualTo("card-test-77");
        assertThat(cardNode.get("x").asDouble()).isEqualTo(320.0);
        assertThat(cardNode.get("y").asDouble()).isEqualTo(180.0);
        assertThat(cardNode.get("by").asLong()).isEqualTo(userA.getId());

        // 5. Phase 5 integration: triggering vote pool and canvas image broadcasts
        msgsA.clear();
        msgsB.clear();

        // Broadcast VOTE_POOL_UPDATED
        Map<String, Object> votePoolPayload = Map.of(
                "type", "VOTE_POOL_UPDATED",
                "poolId", UUID.randomUUID().toString(),
                "status", "OPEN",
                "title", "LAN Sprint 3 Pool",
                "votesPerParticipant", 3,
                "allowMultipleVotesPerIdea", true,
                "tallies", java.util.List.of()
        );
        webSocketHandler.broadcastToRoom(roomA.getId(), votePoolPayload, null);

        String vpA = null, vpB = null;
        for (int i = 0; i < 5; i++) {
            String mA = msgsA.poll(2, TimeUnit.SECONDS);
            if (mA != null && mA.contains("\"VOTE_POOL_UPDATED\"")) vpA = mA;
            String mB = msgsB.poll(2, TimeUnit.SECONDS);
            if (mB != null && mB.contains("\"VOTE_POOL_UPDATED\"")) vpB = mB;
            if (vpA != null && vpB != null) break;
        }
        assertThat(vpA).isNotNull();
        assertThat(vpB).isNotNull();

        // Broadcast CANVAS_IMAGE_UPDATED
        msgsA.clear();
        msgsB.clear();
        Map<String, Object> canvasImgPayload = Map.of(
                "type", "CANVAS_IMAGE_UPDATED",
                "roomId", roomA.getId().toString(),
                "image", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "x", 120.0,
                        "y", 220.0,
                        "width", 300.0,
                        "height", 200.0
                )
        );
        webSocketHandler.broadcastToRoom(roomA.getId(), canvasImgPayload, null);

        String imgA = null, imgB = null;
        for (int i = 0; i < 5; i++) {
            String mA = msgsA.poll(2, TimeUnit.SECONDS);
            if (mA != null && mA.contains("\"CANVAS_IMAGE_UPDATED\"")) imgA = mA;
            String mB = msgsB.poll(2, TimeUnit.SECONDS);
            if (mB != null && mB.contains("\"CANVAS_IMAGE_UPDATED\"")) imgB = mB;
            if (imgA != null && imgB != null) break;
        }
        assertThat(imgA).isNotNull();
        assertThat(imgB).isNotNull();

        // 6. User B disconnects -> User A receives updated presence showing 1 participant
        sB.close();
        String presAfterLeave = null;
        for (int i = 0; i < 5; i++) {
            String m = msgsA.poll(2, TimeUnit.SECONDS);
            if (m != null && m.contains("\"presence\"") && !m.contains("user-b")) {
                presAfterLeave = m;
                break;
            }
        }
        assertThat(presAfterLeave).isNotNull();
        JsonNode leaveNode = objectMapper.readTree(presAfterLeave);
        assertThat(leaveNode.get("participants").size()).isEqualTo(1);
        assertThat(leaveNode.get("participants").get(0).get("name").asText()).isEqualTo("user-a");

        sA.close();
    }
}
