package com.sme.maintenance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CommentStoriesAuditTests.TestJwtConfiguration.class)
public class CommentStoriesAuditTests {

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

    private String createRoom(String ownerSub, String name) throws Exception {
        String res = mockMvc.perform(post("/api/rooms")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id").toString();
    }

    private String joinRoom(String memberSub, String invitationCode) throws Exception {
        String res = mockMvc.perform(post("/api/rooms/join")
                        .with(authenticatedAs(memberSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + invitationCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id").toString();
    }

    private String createIdea(String authorSub, String roomId, String text) throws Exception {
        String res = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                        .with(authenticatedAs(authorSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\",\"title\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id").toString();
    }

    @Test
    @DisplayName("Audit Harness: 3-user room and idea baseline setup")
    void testAuditHarnessSetup() throws Exception {
        String ownerSub = "comment-audit-owner-" + UUID.randomUUID();
        String participantSub = "comment-audit-participant-" + UUID.randomUUID();
        String nonParticipantSub = "comment-audit-outsider-" + UUID.randomUUID();

        // 1. Owner creates room
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comment Audit Room\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String roomId = JsonPath.read(roomJson, "$.id").toString();
        String invitationCode = JsonPath.read(roomJson, "$.invitationCode").toString();

        // 2. Participant joins room
        joinRoom(participantSub, invitationCode);

        // 3. Non-participant is forbidden from viewing room details
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .with(authenticatedAs(nonParticipantSub)))
                .andExpect(status().isForbidden());

        // 4. Owner creates an idea
        String ideaId = createIdea(ownerSub, roomId, "Idea for commenting");
        assertThat(ideaId).isNotNull();

        // 5. Participant can see the idea (with explicit sortBy to avoid null sortBy NPE bug in IdeaService)
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?sortBy=createdAt")
                        .with(authenticatedAs(participantSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ideaId));
    }

    // =========================================================================
    // Story #32: Comment on an idea
    // =========================================================================
    @Nested
    @DisplayName("Story #32: Comment on an idea")
    class Story32Audit {
        @Test
        @DisplayName("32.1 Add comment to an idea")
        void testCommentOnIdea() throws Exception {
            String owner = "s32-owner-" + UUID.randomUUID();
            String member = "s32-member-" + UUID.randomUUID();
            String outsider = "s32-outsider-" + UUID.randomUUID();

            String roomId = createRoom(owner, "Story 32 Room");
            String roomInfo = mockMvc.perform(get("/api/rooms/" + roomId).with(authenticatedAs(owner)))
                    .andReturn().getResponse().getContentAsString();
            String code = JsonPath.read(roomInfo, "$.invitationCode").toString();
            joinRoom(member, code);

            String ideaId = createIdea(owner, roomId, "Story 32 Idea");

            // 1. Member can add a comment to the idea
            String commentResp = mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Great idea from member!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.ideaId").value(ideaId))
                    .andExpect(jsonPath("$.content").value("Great idea from member!"))
                    .andExpect(jsonPath("$.author.username").value(member))
                    .andExpect(jsonPath("$.replies").isArray())
                    .andReturn().getResponse().getContentAsString();
            String commentId = JsonPath.read(commentResp, "$.id").toString();

            // 2. Owner can also add a comment (using "text" field)
            mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Thanks for the input!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("Thanks for the input!"));

            // 3. Member can list comments
            mockMvc.perform(get("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(commentId));

            // 4. Non-member cannot add comment (403)
            mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(outsider))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Outsider trying to comment\"}"))
                    .andExpect(status().isForbidden());

            // 5. Non-member cannot list comments (403)
            mockMvc.perform(get("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(outsider)))
                    .andExpect(status().isForbidden());

            // 6. Empty comment content is rejected (400)
            mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"   \"}"))
                    .andExpect(status().isBadRequest());

            // 7. Non-existent idea returns 404
            mockMvc.perform(post("/api/ideas/" + UUID.randomUUID() + "/comments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Ghost idea comment\"}"))
                    .andExpect(status().isNotFound());

            // 8. Closed room blocks comment creation (403)
            mockMvc.perform(post("/api/rooms/" + roomId + "/close")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Blocked in closed room\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Story #33: Edit or delete own comments
    // =========================================================================
    @Nested
    @DisplayName("Story #33: Edit or delete own comments")
    class Story33Audit {
        @Test
        @DisplayName("33.1 Edit or delete own comment")
        void testEditOrDeleteComment() throws Exception {
            String owner = "s33-owner-" + UUID.randomUUID();
            String author = "s33-author-" + UUID.randomUUID();
            String otherMember = "s33-other-" + UUID.randomUUID();
            String outsider = "s33-outsider-" + UUID.randomUUID();

            String roomId = createRoom(owner, "Story 33 Room");
            String roomInfo = mockMvc.perform(get("/api/rooms/" + roomId).with(authenticatedAs(owner)))
                    .andReturn().getResponse().getContentAsString();
            String code = JsonPath.read(roomInfo, "$.invitationCode").toString();
            joinRoom(author, code);
            joinRoom(otherMember, code);

            String ideaId = createIdea(owner, roomId, "Story 33 Idea");

            // Author creates comment
            String commentResp = mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(author))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Original comment text\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String commentId = JsonPath.read(commentResp, "$.id").toString();

            // 1. Other member cannot edit author's comment (403)
            mockMvc.perform(put("/api/comments/" + commentId)
                            .with(authenticatedAs(otherMember))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Tampered content\"}"))
                    .andExpect(status().isForbidden());

            // 2. Outsider cannot edit author's comment (403)
            mockMvc.perform(put("/api/comments/" + commentId)
                            .with(authenticatedAs(outsider))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Outsider tamper\"}"))
                    .andExpect(status().isForbidden());

            // 3. Author cannot edit with empty content (400)
            mockMvc.perform(put("/api/comments/" + commentId)
                            .with(authenticatedAs(author))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"  \"}"))
                    .andExpect(status().isBadRequest());

            // 4. Editing non-existent comment returns 404
            mockMvc.perform(put("/api/comments/" + UUID.randomUUID())
                            .with(authenticatedAs(author))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Ghost edit\"}"))
                    .andExpect(status().isNotFound());

            // 5. Author successfully edits their own comment
            mockMvc.perform(put("/api/comments/" + commentId)
                            .with(authenticatedAs(author))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Edited comment text\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(commentId))
                    .andExpect(jsonPath("$.content").value("Edited comment text"));

            // 6. Other member cannot delete author's comment (403)
            mockMvc.perform(delete("/api/comments/" + commentId)
                            .with(authenticatedAs(otherMember)))
                    .andExpect(status().isForbidden());

            // 7. Deleting non-existent comment returns 404
            mockMvc.perform(delete("/api/comments/" + UUID.randomUUID())
                            .with(authenticatedAs(author)))
                    .andExpect(status().isNotFound());

            // 8. Closed room blocks edit and delete (403)
            mockMvc.perform(post("/api/rooms/" + roomId + "/close")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/comments/" + commentId)
                            .with(authenticatedAs(author))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Edit during closed\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/comments/" + commentId)
                            .with(authenticatedAs(author)))
                    .andExpect(status().isForbidden());

            // 9. Owner re-opens room
            mockMvc.perform(post("/api/rooms/" + roomId + "/open")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk());

            // 10. Author deletes their own comment (200)
            mockMvc.perform(delete("/api/comments/" + commentId)
                            .with(authenticatedAs(author)))
                    .andExpect(status().isOk());

            // 11. Comments list is now empty
            mockMvc.perform(get("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(author)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // =========================================================================
    // Story #34: Reply to a comment
    // =========================================================================
    @Nested
    @DisplayName("Story #34: Reply to a comment")
    class Story34Audit {
        @Test
        @DisplayName("34.1 Reply to a comment")
        void testReplyToComment() throws Exception {
            String owner = "s34-owner-" + UUID.randomUUID();
            String member1 = "s34-member1-" + UUID.randomUUID();
            String member2 = "s34-member2-" + UUID.randomUUID();
            String outsider = "s34-outsider-" + UUID.randomUUID();

            String roomId = createRoom(owner, "Story 34 Room");
            String roomInfo = mockMvc.perform(get("/api/rooms/" + roomId).with(authenticatedAs(owner)))
                    .andReturn().getResponse().getContentAsString();
            String code = JsonPath.read(roomInfo, "$.invitationCode").toString();
            joinRoom(member1, code);
            joinRoom(member2, code);

            String ideaId = createIdea(owner, roomId, "Story 34 Idea");

            // Member1 adds top-level comment
            String parentResp = mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Top-level comment\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String parentId = JsonPath.read(parentResp, "$.id").toString();

            // 1. Member2 replies via POST /api/comments/{commentId}/replies
            String replyResp = mockMvc.perform(post("/api/comments/" + parentId + "/replies")
                            .with(authenticatedAs(member2))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"First reply by member2\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.parentId").value(parentId))
                    .andExpect(jsonPath("$.content").value("First reply by member2"))
                    .andExpect(jsonPath("$.author.username").value(member2))
                    .andReturn().getResponse().getContentAsString();
            String replyId = JsonPath.read(replyResp, "$.id").toString();

            // 2. Member1 replies via POST /api/ideas/{ideaId}/comments with parentId
            mockMvc.perform(post("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Second reply by member1\",\"parentId\":\"" + parentId + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(parentId));

            // 3. Listing comments returns top-level comment with nested replies
            mockMvc.perform(get("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(parentId))
                    .andExpect(jsonPath("$[0].replies.length()").value(2))
                    .andExpect(jsonPath("$[0].replies[0].id").value(replyId))
                    .andExpect(jsonPath("$[0].replies[0].content").value("First reply by member2"));

            // 4. One-level reply limit: replying to a reply is rejected with 400
            mockMvc.perform(post("/api/comments/" + replyId + "/replies")
                            .with(authenticatedAs(member1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Nested reply attempt\"}"))
                    .andExpect(status().isBadRequest());

            // 5. Non-member cannot reply (403)
            mockMvc.perform(post("/api/comments/" + parentId + "/replies")
                            .with(authenticatedAs(outsider))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Outsider reply\"}"))
                    .andExpect(status().isForbidden());

            // 6. Replying to non-existent comment returns 404
            mockMvc.perform(post("/api/comments/" + UUID.randomUUID() + "/replies")
                            .with(authenticatedAs(member1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Ghost reply\"}"))
                    .andExpect(status().isNotFound());

            // 7. Closed room blocks replying (403)
            mockMvc.perform(post("/api/rooms/" + roomId + "/close")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/comments/" + parentId + "/replies")
                            .with(authenticatedAs(member2))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Reply in closed room\"}"))
                    .andExpect(status().isForbidden());

            // 8. Open room and test cascade delete: deleting parent comment removes replies
            mockMvc.perform(post("/api/rooms/" + roomId + "/open")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/comments/" + parentId)
                            .with(authenticatedAs(member1)))
                    .andExpect(status().isOk());

            // List comments -> 0 comments left (no orphaned replies)
            mockMvc.perform(get("/api/ideas/" + ideaId + "/comments")
                            .with(authenticatedAs(member1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
