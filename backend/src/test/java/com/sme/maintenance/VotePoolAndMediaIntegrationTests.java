package com.sme.maintenance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VotePoolAndMediaIntegrationTests.TestJwtConfiguration.class)
public class VotePoolAndMediaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("JWT decoding is not used in MockMvc tests");
            };
        }
    }

    private RequestPostProcessor authenticatedAs(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("preferred_username", subject));
    }

    record CreatedRoom(String id, String invitationCode) {}

    private CreatedRoom createRoom(String ownerSub, String name) throws Exception {
        String res = mockMvc.perform(post("/api/rooms")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id").toString();
        String code = JsonPath.read(res, "$.invitationCode").toString();
        return new CreatedRoom(id, code);
    }

    private void joinRoom(String memberSub, String invitationCode) throws Exception {
        mockMvc.perform(post("/api/rooms/join")
                        .with(authenticatedAs(memberSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + invitationCode + "\"}"))
                .andExpect(status().isOk());
    }

    private String createIdea(String userSub, String roomId, String title) throws Exception {
        String res = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                        .with(authenticatedAs(userSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"text\":\"" + title + " content\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id").toString();
    }

    // =========================================================================
    // Story #37: Vote Pool Integration Tests
    // =========================================================================

    @Test
    @DisplayName("37.1 Creator creates pool (201), non-creator rejected (403), active pool uniqueness enforced (409)")
    void testCreateVotePoolRules() throws Exception {
            String owner = "vp-owner-1";
            String member = "vp-member-1";
            CreatedRoom room = createRoom(owner, "Pool Room 1");
            String roomId = room.id();
            joinRoom(member, room.invitationCode());

            // Non-creator attempt -> 403
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Sprint Priorities\",\"votesPerParticipant\":3}"))
                    .andExpect(status().isForbidden());

            // Creator creates pool -> 201 Created
            String poolJson = mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Sprint Priorities\",\"votesPerParticipant\":3,\"allowMultipleVotesPerIdea\":true}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Sprint Priorities"))
                    .andExpect(jsonPath("$.votesPerParticipant").value(3))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.myVotesRemaining").value(3))
                    .andReturn().getResponse().getContentAsString();

            // Second active pool attempt -> 409 Conflict
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Second Active Pool\",\"votesPerParticipant\":3}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("37.2 Voting decrements budget, exceeding budget returns 409 VOTE_BUDGET_EXCEEDED, unvoting refunds, normal upvotes unaffected")
        void testVotingBudgetAndLifecycle() throws Exception {
            String owner = "vp-owner-2";
            String member = "vp-member-2";
            CreatedRoom room = createRoom(owner, "Pool Room 2");
            String roomId = room.id();
            joinRoom(member, room.invitationCode());

            String idea1 = createIdea(owner, roomId, "Feature Alpha");
            String idea2 = createIdea(member, roomId, "Feature Beta");

            // Normal Sprint 1 upvote on idea1
            mockMvc.perform(post("/api/ideas/" + idea1 + "/vote")
                            .with(authenticatedAs(member)))
                    .andExpect(status().isOk());

            // Creator starts pool with budget = 2
            String poolRes = mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Two Vote Budget\",\"votesPerParticipant\":2,\"allowMultipleVotesPerIdea\":true}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String poolId = JsonPath.read(poolRes, "$.id").toString();

            // Vote 1 on idea1 -> budget remaining = 1
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/votes")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ideaId\":\"" + idea1 + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myVotesUsed").value(1))
                    .andExpect(jsonPath("$.myVotesRemaining").value(1));

            // Vote 2 on idea1 -> budget remaining = 0
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/votes")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ideaId\":\"" + idea1 + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myVotesUsed").value(2))
                    .andExpect(jsonPath("$.myVotesRemaining").value(0));

            // Vote 3 (budget exceeded) -> 409 Conflict with VOTE_BUDGET_EXCEEDED
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/votes")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ideaId\":\"" + idea2 + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("VOTE_BUDGET_EXCEEDED"))
                    .andExpect(jsonPath("$.remaining").value(0))
                    .andExpect(jsonPath("$.limit").value(2));

            // Remove 1 vote on idea1 -> refunds 1 vote
            mockMvc.perform(delete("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/votes/" + idea1)
                            .with(authenticatedAs(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myVotesUsed").value(1))
                    .andExpect(jsonPath("$.myVotesRemaining").value(1));

            // Creator closes pool -> 200 OK, status = CLOSED, tallies ranked
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/close")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.tallies[0].ideaId").value(idea1))
                    .andExpect(jsonPath("$.tallies[0].poolVotes").value(1));

            // Voting on closed pool rejected -> 409
            mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/votes")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ideaId\":\"" + idea2 + "\"}"))
                    .andExpect(status().isConflict());

            // Verify normal idea upvote count on idea1 is still 1 (unaffected)
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '" + idea1 + "')].voteCount").value(org.hamcrest.Matchers.hasItem(1)));
        }

        @Test
        @DisplayName("37.3 Parallel voting concurrency test: exactly 1 thread succeeds when remaining = 1, others get 409 VOTE_BUDGET_EXCEEDED")
        void testParallelVotingBudgetExhaustion() throws Exception {
            String owner = "vp-owner-3";
            String member = "vp-member-3";
            CreatedRoom room = createRoom(owner, "Concurrency Pool Room");
            String roomId = room.id();
            joinRoom(member, room.invitationCode());

            String idea1 = createIdea(owner, roomId, "Contested Idea");

            // Pool with 1 vote budget
            String poolRes = mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"1 Vote Concurrency\",\"votesPerParticipant\":1}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String poolId = JsonPath.read(poolRes, "$.id").toString();

            // Run 5 parallel threads trying to cast the single available vote
            int numThreads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < numThreads; i++) {
                tasks.add(() -> {
                    MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/vote-pools/" + poolId + "/votes")
                                    .with(authenticatedAs(member))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"ideaId\":\"" + idea1 + "\"}"))
                            .andReturn();
                    return result.getResponse().getStatus();
                });
            }

            List<Future<Integer>> futures = executor.invokeAll(tasks);
            executor.shutdown();

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            for (Future<Integer> f : futures) {
                int status = f.get();
                if (status == 200) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    conflictCount.incrementAndGet();
                }
            }

            // Exactly 1 thread must succeed, and all 4 other threads must receive 409 Conflict
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(numThreads - 1);
        }

    // =========================================================================
    // Story #26: Idea Attachments & Canvas Images Integration Tests
    // =========================================================================

        @Test
        @DisplayName("26.1 Idea attachments: max 3 attachments enforced (4th returns 409), deletion works")
        void testIdeaAttachmentCapAndDeletion() throws Exception {
            String owner = "media-owner-1";
            String member = "media-member-1";
            CreatedRoom room = createRoom(owner, "Media Room 1");
            String roomId = room.id();
            joinRoom(member, room.invitationCode());

            String ideaId = createIdea(member, roomId, "Visual Idea");

            // Add attachment 1
            String att1Json = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas/" + ideaId + "/attachments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mediaId\":\"media-uuid-1\",\"contentType\":\"image/png\",\"width\":100,\"height\":100}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.mediaId").value("media-uuid-1"))
                    .andReturn().getResponse().getContentAsString();
            String att1Id = JsonPath.read(att1Json, "$.id").toString();

            // Add attachment 2
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas/" + ideaId + "/attachments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mediaId\":\"media-uuid-2\",\"contentType\":\"image/png\",\"width\":100,\"height\":100}"))
                    .andExpect(status().isCreated());

            // Add attachment 3
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas/" + ideaId + "/attachments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mediaId\":\"media-uuid-3\",\"contentType\":\"image/png\",\"width\":100,\"height\":100}"))
                    .andExpect(status().isCreated());

            // Add attachment 4 (exceeds cap of 3) -> 409 Conflict
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas/" + ideaId + "/attachments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mediaId\":\"media-uuid-4\",\"contentType\":\"image/png\",\"width\":100,\"height\":100}"))
                    .andExpect(status().isConflict());

            // Fetch bundle
            mockMvc.perform(get("/api/rooms/" + roomId + "/attachments")
                            .with(authenticatedAs(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ideaAttachmentsByIdeaId." + ideaId).isArray())
                    .andExpect(jsonPath("$.ideaAttachmentsByIdeaId." + ideaId + ".length()").value(3));

            // Delete attachment 1 -> 204 No Content
            mockMvc.perform(delete("/api/rooms/" + roomId + "/ideas/" + ideaId + "/attachments/" + att1Id)
                            .with(authenticatedAs(member)))
                    .andExpect(status().isNoContent());

            // Attachment count is now 2
            mockMvc.perform(get("/api/rooms/" + roomId + "/attachments")
                            .with(authenticatedAs(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ideaAttachmentsByIdeaId." + ideaId + ".length()").value(2));
        }

        @Test
        @DisplayName("26.2 Canvas images: add, patch coordinates, delete authorization")
        void testCanvasImageLifecycle() throws Exception {
            String owner = "canvas-owner-1";
            String member = "canvas-member-1";
            String outsider = "canvas-outsider-1";
            CreatedRoom room = createRoom(owner, "Canvas Room 1");
            String roomId = room.id();
            joinRoom(member, room.invitationCode());

            // Add canvas image
            String imgJson = mockMvc.perform(post("/api/rooms/" + roomId + "/canvas-images")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mediaId\":\"canvas-media-1\",\"contentType\":\"image/jpeg\",\"x\":150.0,\"y\":200.0,\"width\":300.0,\"height\":200.0,\"naturalWidth\":1200,\"naturalHeight\":800,\"zIndex\":1}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.x").value(150.0))
                    .andExpect(jsonPath("$.y").value(200.0))
                    .andExpect(jsonPath("$.width").value(300.0))
                    .andReturn().getResponse().getContentAsString();
            String imgId = JsonPath.read(imgJson, "$.id").toString();

            // Patch coordinates and resize
            mockMvc.perform(patch("/api/rooms/" + roomId + "/canvas-images/" + imgId)
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"x\":250.0,\"y\":300.0,\"width\":400.0,\"height\":250.0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.x").value(250.0))
                    .andExpect(jsonPath("$.y").value(300.0))
                    .andExpect(jsonPath("$.width").value(400.0));

            // Outsider attempt to delete -> 403 Forbidden
            joinRoom(outsider, room.invitationCode());
            mockMvc.perform(delete("/api/rooms/" + roomId + "/canvas-images/" + imgId)
                            .with(authenticatedAs(outsider)))
                    .andExpect(status().isForbidden());

            // Room owner can delete -> 204 No Content
            mockMvc.perform(delete("/api/rooms/" + roomId + "/canvas-images/" + imgId)
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("26.3 Internal room access endpoint security check")
        void testInternalRoomAccessCheck() throws Exception {
            String owner = "internal-owner-1";
            String member = "internal-member-1";
            CreatedRoom room = createRoom(owner, "Internal Access Room");
            String roomId = room.id();
            joinRoom(member, room.invitationCode());

            // Without service key -> 403 Forbidden
            mockMvc.perform(get("/internal/rooms/" + roomId + "/access/" + member))
                    .andExpect(status().isForbidden());

            // Wrong service key -> 403 Forbidden
            mockMvc.perform(get("/internal/rooms/" + roomId + "/access/" + member)
                            .header("X-Internal-Service-Key", "wrong-key"))
                    .andExpect(status().isForbidden());

            // Non-existent room -> 404 Not Found
            mockMvc.perform(get("/internal/rooms/" + UUID.randomUUID() + "/access/" + member)
                            .header("X-Internal-Service-Key", "mindspark-internal-dev-key"))
                    .andExpect(status().isNotFound());

            // Member check -> 200 OK, isMember=true, isOwner=false
            mockMvc.perform(get("/internal/rooms/" + roomId + "/access/" + member)
                            .header("X-Internal-Service-Key", "mindspark-internal-dev-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isMember").value(true))
                    .andExpect(jsonPath("$.isOwner").value(false))
                    .andExpect(jsonPath("$.roomClosed").value(false));

            // Owner check -> 200 OK, isMember=true, isOwner=true
            mockMvc.perform(get("/internal/rooms/" + roomId + "/access/" + owner)
                            .header("X-Internal-Service-Key", "mindspark-internal-dev-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isMember").value(true))
                    .andExpect(jsonPath("$.isOwner").value(true));
        }
}
