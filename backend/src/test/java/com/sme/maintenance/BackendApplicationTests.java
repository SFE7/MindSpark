package com.sme.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

        @Test
        void guestCanCreateJoinLeaveAndReopenRoom() throws Exception {
        String owner = "11111111-1111-1111-1111-111111111111";
        String guest = "22222222-2222-2222-2222-222222222222";

        String room = mockMvc.perform(post("/api/rooms")
                .header("X-Guest-Id", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Product ideas\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitationCode").isString())
            .andReturn().getResponse().getContentAsString();

        String code = room.replaceAll(".*\"invitationCode\":\"([^\"]+)\".*", "$1");
        String roomId = room.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/rooms/join")
                .header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.member").value(true));

        String idea = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Add a feedback board\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String ideaId = idea.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/ideas/" + ideaId + "/vote").header("X-Guest-Id", owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.voteCount").value(1));
        mockMvc.perform(delete("/api/rooms/" + roomId + "/leave").header("X-Guest-Id", guest))
            .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(false));
        mockMvc.perform(post("/api/rooms/" + roomId + "/reopen").header("X-Guest-Id", owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(true));
    }

    @Test
    void participantJoinsLaterAndAlreadyJoinedCanSeeIdeasAndVotes() throws Exception {
        String owner = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String member1 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String laterMember = "cccccccc-cccc-cccc-cccc-cccccccccccc";

        // Owner creates room
        String room = mockMvc.perform(post("/api/rooms")
                .header("X-Guest-Id", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Brainstorming Sprint\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String code = room.replaceAll(".*\"invitationCode\":\"([^\"]+)\".*", "$1");
        String roomId = room.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        // Member 1 joins already
        mockMvc.perform(post("/api/rooms/join")
                .header("X-Guest-Id", member1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk());

        // Member 1 posts an idea with coordinates and details
        String ideaResponse = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                .header("X-Guest-Id", member1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"AI Assistant\",\"description\":\"Add AI co-pilot\",\"x\":350.0,\"y\":180.0,\"color\":\"#ed8d74\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("AI Assistant"))
            .andExpect(jsonPath("$.description").value("Add AI co-pilot"))
            .andExpect(jsonPath("$.x").value(350.0))
            .andExpect(jsonPath("$.y").value(180.0))
            .andExpect(jsonPath("$.color").value("#ed8d74"))
            .andReturn().getResponse().getContentAsString();
        String ideaId = ideaResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        // Owner (already joined) polls/lists ideas -> sees Member 1's idea
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .header("X-Guest-Id", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("AI Assistant"))
            .andExpect(jsonPath("$[0].x").value(350.0));

        // Later participant joins with invitation code
        mockMvc.perform(post("/api/rooms/join")
                .header("X-Guest-Id", laterMember)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.member").value(true));

        // Later participant fetches ideas immediately upon joining -> sees the idea!
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .header("X-Guest-Id", laterMember))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("AI Assistant"))
            .andExpect(jsonPath("$[0].description").value("Add AI co-pilot"));

        // Member moves/drags idea to new position on canvas
        mockMvc.perform(put("/api/ideas/" + ideaId)
                .header("X-Guest-Id", member1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"x\":620.0,\"y\":310.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.x").value(620.0))
            .andExpect(jsonPath("$.y").value(310.0))
            .andExpect(jsonPath("$.title").value("AI Assistant"));

        // Non-author participant (laterMember) can also reposition/cluster the idea collaboratively
        mockMvc.perform(put("/api/ideas/" + ideaId)
                .header("X-Guest-Id", laterMember)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"AI Assistant\",\"description\":\"Add AI co-pilot\",\"x\":750.0,\"y\":400.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.x").value(750.0))
            .andExpect(jsonPath("$.y").value(400.0));

        // All participants see the updated dragged position
        mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                .header("X-Guest-Id", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].x").value(750.0))
            .andExpect(jsonPath("$[0].y").value(400.0));
    }
}
