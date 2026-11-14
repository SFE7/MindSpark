package com.sme.maintenance.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;

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
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token.contains("owner") ? "owner-sub" : "member-sub")
                    .claim("email", token.contains("owner") ? "owner@example.com" : "member@example.com")
                    .claim("preferred_username", token.contains("owner") ? "owner" : "member")
                    .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                    .build();
        }
    }

    @Test
    void socketAuthenticatesAndReceivesRoomUpdatedBroadcastOnMutation() throws Exception {
        User owner = userRepository.findByKeycloakId("owner-sub")
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername("owner");
                    u.setEmail("owner@example.com");
                    u.setKeycloakId("owner-sub");
                    u.setCreatedAt(new java.sql.Date(System.currentTimeMillis()));
                    return userRepository.save(u);
                });

        Room room = roomService.create("Original Room", owner.getId());
        UUID roomId = room.getId();

        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/rooms").get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();

        // Send auth message
        String authPayload = objectMapper.writeValueAsString(Map.of(
                "type", "auth",
                "token", "owner-token",
                "roomId", roomId.toString(),
                "clientId", "c-test-integration"
        ));
        session.sendMessage(new TextMessage(authPayload));

        // First message may be auth.success ack or room.offline event
        String first = messages.poll(5, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        JsonNode firstNode = objectMapper.readTree(first);
        assertThat(firstNode.get("type").asText()).isIn("auth.success", "room.offline");

        // Mutate room via RoomService (commits and triggers broadcastAfterCommit)
        roomService.update(roomId, "Renamed Room", owner.getId());

        // Wait for room.updated event (may need to drain intermediate event if any)
        String eventPayload = null;
        for (int i = 0; i < 3; i++) {
            String m = messages.poll(5, TimeUnit.SECONDS);
            if (m != null && m.contains("room.updated")) {
                eventPayload = m;
                break;
            }
        }

        assertThat(eventPayload).isNotNull();
        JsonNode eventNode = objectMapper.readTree(eventPayload);
        assertThat(eventNode.get("type").asText()).isEqualTo("room.updated");
        assertThat(eventNode.get("room").get("name").asText()).isEqualTo("Renamed Room");

        session.close();
    }
}
