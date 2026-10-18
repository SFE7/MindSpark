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

}
