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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    // Status: Not implemented in codebase (no endpoints, entities, or services)
    // =========================================================================
    @Nested
    @DisplayName("Story #32: Comment on an idea [NOT IMPLEMENTED]")
    class Story32Audit {
        @Test
        @Disabled("Story #32 not implemented")
        @DisplayName("32.1 Add comment to an idea")
        void testCommentOnIdea() {
        }
    }

    // =========================================================================
    // Story #33: Edit or delete own comments
    // Status: Not implemented in codebase (no endpoints, entities, or services)
    // =========================================================================
    @Nested
    @DisplayName("Story #33: Edit or delete own comments [NOT IMPLEMENTED]")
    class Story33Audit {
        @Test
        @Disabled("Story #33 not implemented")
        @DisplayName("33.1 Edit or delete own comment")
        void testEditOrDeleteComment() {
        }
    }

    // =========================================================================
    // Story #34: Reply to a comment
    // Status: Not implemented in codebase (no endpoints, entities, or services)
    // =========================================================================
    @Nested
    @DisplayName("Story #34: Reply to a comment [NOT IMPLEMENTED]")
    class Story34Audit {
        @Test
        @Disabled("Story #34 not implemented")
        @DisplayName("34.1 Reply to a comment")
        void testReplyToComment() {
        }
    }
}
