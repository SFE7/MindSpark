package com.sme.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.presence.PresenceSweeper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PresenceIntegrationTests.TestJwtConfig.class)
class PresenceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private PresenceSweeper presenceSweeper;

    private ControllableClock testClock;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Not used in MockMvc");
            };
        }
    }

    private static class ControllableClock extends Clock {
        private Instant currentInstant;

        public ControllableClock(Instant start) {
            this.currentInstant = start;
        }

        public void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    private RequestPostProcessor userAuth(String userId) {
        return jwt().jwt(jwt -> jwt
                .subject(userId)
                .claim("email", userId + "@mindspark.local")
                .claim("preferred_username", userId));
    }

    @BeforeEach
    void setup() {
        testClock = new ControllableClock(Instant.now());
        presenceService.setClock(testClock);
        presenceService.reset();
    }

    @Test
    void applicationContextLoadsWithoutCircularDependency() {
        // Verifies PresenceService, PresenceSweeper, and RoomService start cleanly
    }

    @Test
    void nonMemberPresenceCallsAreRejectedWithForbidden() throws Exception {
        String ownerId = "owner-test-" + UUID.randomUUID();
        String outsiderId = "outsider-test-" + UUID.randomUUID();

        String roomRes = mockMvc.perform(post("/api/rooms")
                        .with(userAuth(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Collab Room\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String roomId = JsonPath.read(roomRes, "$.id");

        mockMvc.perform(post("/api/rooms/" + roomId + "/presence")
                        .with(userAuth(outsiderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"tab-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberDisconnectRemovesFromRoomKeepsIdeasAndAllowsRejoin() throws Exception {
        String ownerId = "owner-p-" + UUID.randomUUID();
        String memberId = "member-p-" + UUID.randomUUID();

        // 1. Owner creates room
        String roomRes = mockMvc.perform(post("/api/rooms")
                        .with(userAuth(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Design Sprint\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String roomId = JsonPath.read(roomRes, "$.id");
        String inviteCode = JsonPath.read(roomRes, "$.invitationCode");

        // 2. Member joins room
        mockMvc.perform(post("/api/rooms/join")
                        .with(userAuth(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk());

        // 3. Member posts idea
        String ideaRes = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                        .with(userAuth(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sticky Idea\",\"description\":\"Keep me\",\"x\":120,\"y\":80}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String ideaId = JsonPath.read(ideaRes, "$.id");

        // 4. Member sends presence heartbeat
        mockMvc.perform(post("/api/rooms/" + roomId + "/presence")
                        .with(userAuth(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"member-tab-1\"}"))
                .andExpect(status().isOk());

        // Verify member is in room
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .with(userAuth(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        // 5. Member disconnects: advance clock by 65s (exceeds 60s timeout)
        testClock.advance(Duration.ofSeconds(65));

        // 6. Direct sweep execution
        presenceSweeper.sweepExpired();

        // 7. Verify member is removed from room
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .with(userAuth(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1));

        // 8. Verify member's idea STILL exists
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                        .with(userAuth(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ideaId + "')].title").value("Sticky Idea"));

        // 9. Member can rejoin by code
        mockMvc.perform(post("/api/rooms/join")
                        .with(userAuth(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));
    }
}
