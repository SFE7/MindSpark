package com.sme.maintenance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
@Import(BackendApplicationTests.TestJwtConfiguration.class)
class BackendApplicationTests {

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

    @Test
    void contextLoads() {
    }

    @Test
    void roomEndpointsRequireAnAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());
    }

        @Test
    void authenticatedUserCanCreateJoinLeaveAndReopenRoom() throws Exception {
        String owner = "11111111-1111-1111-1111-111111111111";
        String member = "22222222-2222-2222-2222-222222222222";

        String room = mockMvc.perform(post("/api/rooms")
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Product ideas\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitationCode").isString())
            .andReturn().getResponse().getContentAsString();

        String code = room.replaceAll(".*\"invitationCode\":\"([^\"]+)\".*", "$1");
        String roomId = room.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.member").value(true));

        String idea = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Add a feedback board\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String ideaId = JsonPath.read(idea, "$.id").toString();

        mockMvc.perform(post("/api/ideas/" + ideaId + "/vote").with(authenticatedAs(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.voteCount").value(1));
        mockMvc.perform(delete("/api/rooms/" + roomId + "/leave").with(authenticatedAs(member)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(false));
        mockMvc.perform(post("/api/rooms/" + roomId + "/reopen").with(authenticatedAs(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(true));
    }

    @Test
    void participantJoinsLaterAndAlreadyJoinedCanSeeIdeasAndVotes() throws Exception {
        String owner = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String member1 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String laterMember = "cccccccc-cccc-cccc-cccc-cccccccccccc";

        // Owner creates room
        String room = mockMvc.perform(post("/api/rooms")
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Brainstorming Sprint\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String code = room.replaceAll(".*\"invitationCode\":\"([^\"]+)\".*", "$1");
        String roomId = room.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        // Member 1 joins already
        mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(member1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk());

        // Member 1 posts an idea with coordinates and details
        String ideaResponse = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(member1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"AI Assistant\",\"description\":\"Add AI co-pilot\",\"x\":350.0,\"y\":180.0,\"color\":\"#ed8d74\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("AI Assistant"))
            .andExpect(jsonPath("$.description").value("Add AI co-pilot"))
            .andExpect(jsonPath("$.x").value(350.0))
            .andExpect(jsonPath("$.y").value(180.0))
            .andExpect(jsonPath("$.color").value("#ed8d74"))
            .andReturn().getResponse().getContentAsString();
        String ideaId = JsonPath.read(ideaResponse, "$.id").toString();

        // Owner (already joined) polls/lists ideas -> sees Member 1's idea
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("AI Assistant"))
            .andExpect(jsonPath("$[0].x").value(350.0));

        // Later participant joins with invitation code
        mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(laterMember))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.member").value(true));

        // Later participant fetches ideas immediately upon joining -> sees the idea!
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(laterMember)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("AI Assistant"))
            .andExpect(jsonPath("$[0].description").value("Add AI co-pilot"));

        // Member moves/drags idea to new position on canvas
        mockMvc.perform(put("/api/ideas/" + ideaId)
                .with(authenticatedAs(member1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"x\":620.0,\"y\":310.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.x").value(620.0))
            .andExpect(jsonPath("$.y").value(310.0))
            .andExpect(jsonPath("$.title").value("AI Assistant"));

        // Non-author participant (laterMember) can also reposition/cluster the idea collaboratively
        mockMvc.perform(put("/api/ideas/" + ideaId)
                .with(authenticatedAs(laterMember))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"AI Assistant\",\"description\":\"Add AI co-pilot\",\"x\":750.0,\"y\":400.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.x").value(750.0))
            .andExpect(jsonPath("$.y").value(400.0));

        // All participants see the updated dragged position
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].x").value(750.0))
            .andExpect(jsonPath("$[0].y").value(400.0));
    }

    @Test
    void ownerCanRemoveParticipantAndRemovedUserIsForbidden() throws Exception {
        String ownerSub = "owner-1111-1111-1111-111111111111";
        String memberSub = "member-2222-2222-2222-222222222222";
        String nonOwnerSub = "other-3333-3333-3333-333333333333";

        // Owner creates room
        String room = mockMvc.perform(post("/api/rooms")
                .with(authenticatedAs(ownerSub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Removal Test Room\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(room, "$.invitationCode").toString();
        String roomId = JsonPath.read(room, "$.id").toString();
        Number ownerIdNum = JsonPath.read(room, "$.owner.id");
        Long ownerId = ownerIdNum.longValue();

        // Member joins room
        String joinedRoom = mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(memberSub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(2))
            .andReturn().getResponse().getContentAsString();

        // Find member ID from members list
        net.minidev.json.JSONArray membersArray = JsonPath.read(joinedRoom, "$.members[?(@.id != " + ownerId + ")].id");
        Long memberId = ((Number) membersArray.get(0)).longValue();

        // Member creates an idea
        String ideaResp = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(memberSub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Persistent Idea\",\"description\":\"Should remain after removal\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String ideaId = JsonPath.read(ideaResp, "$.id").toString();

        // Member votes on idea
        mockMvc.perform(post("/api/ideas/" + ideaId + "/vote")
                .with(authenticatedAs(memberSub)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.voteCount").value(1));

        // Owner cannot remove themselves (400)
        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/" + ownerId)
                .with(authenticatedAs(ownerSub)))
            .andExpect(status().isBadRequest());

        // Target must be a member (404 otherwise)
        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/999999")
                .with(authenticatedAs(ownerSub)))
            .andExpect(status().isNotFound());

        // Non-owner cannot remove anyone (403)
        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/" + memberId)
                .with(authenticatedAs(nonOwnerSub)))
            .andExpect(status().isForbidden());

        // Owner removes member successfully
        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/" + memberId)
                .with(authenticatedAs(ownerSub)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(1));

        // Removed member gets 403 on get room
        mockMvc.perform(get("/api/rooms/" + roomId)
                .with(authenticatedAs(memberSub)))
            .andExpect(status().isForbidden());

        // Removed member gets 403 on list ideas
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(memberSub)))
            .andExpect(status().isForbidden());

        // Removed member gets 403 on create idea
        mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(memberSub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Blocked Idea\"}"))
            .andExpect(status().isForbidden());

        // Removed member gets 403 on voting
        mockMvc.perform(post("/api/ideas/" + ideaId + "/vote")
                .with(authenticatedAs(memberSub)))
            .andExpect(status().isForbidden());

        // Removed user's ideas and votes REMAIN
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(ownerSub)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Persistent Idea"))
            .andExpect(jsonPath("$[0].voteCount").value(1));
    }

    @Test
    void closedRoomBlocksMutationsAndAllowsOwnerManagement() throws Exception {
        String owner = "owner-close-1111";
        String member = "member-close-2222";
        String outsider = "outsider-close-3333";

        // Owner creates room
        String room = mockMvc.perform(post("/api/rooms")
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Active Room\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(false))
            .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(room, "$.invitationCode").toString();
        String roomId = JsonPath.read(room, "$.id").toString();

        // Member joins room
        String joined = mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Number ownerIdNum = JsonPath.read(joined, "$.owner.id");
        net.minidev.json.JSONArray membersArray = JsonPath.read(joined, "$.members[?(@.id != " + ownerIdNum + ")].id");
        Long memberId = ((Number) membersArray.get(0)).longValue();

        // Member creates idea
        String ideaResp = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Pre-close Idea\",\"description\":\"Created before close\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String ideaId = JsonPath.read(ideaResp, "$.id").toString();

        // Non-owner cannot close room (403)
        mockMvc.perform(post("/api/rooms/" + roomId + "/close")
                .with(authenticatedAs(member)))
            .andExpect(status().isForbidden());

        // Owner closes room
        mockMvc.perform(post("/api/rooms/" + roomId + "/close")
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(true));

        // When room is closed:
        // 1. Join is blocked with 400
        mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isBadRequest());

        // 2. Room rename/update is blocked with 403
        mockMvc.perform(put("/api/rooms/" + roomId)
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed Closed Room\"}"))
            .andExpect(status().isForbidden());

        // 3. Idea creation is blocked with 403
        mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Should fail\"}"))
            .andExpect(status().isForbidden());

        // 4. Idea update is blocked with 403
        mockMvc.perform(put("/api/ideas/" + ideaId)
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated title\"}"))
            .andExpect(status().isForbidden());

        // 5. Idea voting add is blocked with 403
        mockMvc.perform(post("/api/ideas/" + ideaId + "/vote")
                .with(authenticatedAs(member)))
            .andExpect(status().isForbidden());

        // 6. Idea voting remove is blocked with 403
        mockMvc.perform(delete("/api/ideas/" + ideaId + "/vote")
                .with(authenticatedAs(member)))
            .andExpect(status().isForbidden());

        // 7. Idea deletion is blocked with 403
        mockMvc.perform(delete("/api/ideas/" + ideaId)
                .with(authenticatedAs(member)))
            .andExpect(status().isForbidden());

        // STILL ALLOWED for owner in closed room:
        // A. Owner reopen (re-enter) room
        mockMvc.perform(post("/api/rooms/" + roomId + "/reopen")
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(true));

        // B. Owner remove member
        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/" + memberId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(1));

        // Owner opens room again
        mockMvc.perform(post("/api/rooms/" + roomId + "/open")
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(false));

        // Owner can now rename room
        mockMvc.perform(put("/api/rooms/" + roomId)
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reopened Room\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Reopened Room"));
    }

    @Test
    void ownerCanDeleteRoomAndCascadeVotesAndIdeas() throws Exception {
        String owner = "owner-del-1111";
        String member = "member-del-2222";

        // Create room
        String room = mockMvc.perform(post("/api/rooms")
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"To Delete Room\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(room, "$.invitationCode").toString();
        String roomId = JsonPath.read(room, "$.id").toString();

        // Member joins and posts idea with vote
        mockMvc.perform(post("/api/rooms/join")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk());

        String idea = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Idea To Be Deleted\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String ideaId = JsonPath.read(idea, "$.id").toString();

        mockMvc.perform(post("/api/ideas/" + ideaId + "/vote")
                .with(authenticatedAs(member)))
            .andExpect(status().isOk());

        // Non-owner cannot delete room (403)
        mockMvc.perform(delete("/api/rooms/" + roomId)
                .with(authenticatedAs(member)))
            .andExpect(status().isForbidden());

        // Owner deletes room (200)
        mockMvc.perform(delete("/api/rooms/" + roomId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk());

        // Getting deleted room returns 404
        mockMvc.perform(get("/api/rooms/" + roomId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isNotFound());

        // Listing ideas for deleted room returns 404
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .with(authenticatedAs(owner)))
            .andExpect(status().isNotFound());
    }
}
