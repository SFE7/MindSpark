package com.sme.maintenance;

import com.jayway.jsonpath.JsonPath;
import com.sme.maintenance.drawing.DrawingRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.drawing.Drawing;

import org.junit.jupiter.api.DisplayName;
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

import java.time.Instant;
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
@Import(DrawingIntegrationTests.TestJwtConfiguration.class)
public class DrawingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DrawingRepository drawingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

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

    private void joinRoom(String memberSub, String invitationCode) throws Exception {
        mockMvc.perform(post("/api/rooms/join")
                        .with(authenticatedAs(memberSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationCode\":\"" + invitationCode + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Create, move, and retrieve drawings for all canvas tools")
    void testCreateMoveAndGetDrawings() throws Exception {
        String ownerSub = "drawing-owner-" + UUID.randomUUID();
        String roomId = createRoom(ownerSub, "Drawing Room 1");

        // 1. Create a freehand pen stroke
        String penPayload = "[[10,20],[15,25],[20,30]]";
        String createRes = mockMvc.perform(post("/api/rooms/" + roomId + "/drawings")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"pen\",\"color\":\"#273b3b\",\"width\":5.0,\"opacity\":1.0,\"payload\":\"" + penPayload + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.kind").value("pen"))
                .andExpect(jsonPath("$.payload").value(penPayload))
                .andExpect(jsonPath("$.offsetX").value(0.0))
                .andReturn().getResponse().getContentAsString();

        String drawingId = JsonPath.read(createRes, "$.id").toString();

        // 2. Create a shape (rectangle)
        String shapePayload = "{\"x\":100,\"y\":150,\"width\":80,\"height\":60}";
        mockMvc.perform(post("/api/rooms/" + roomId + "/drawings")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"rectangle\",\"color\":\"#ed8d74\",\"width\":3.0,\"opacity\":1.0,\"payload\":\"{\\\"x\\\":100,\\\"y\\\":150,\\\"width\\\":80,\\\"height\\\":60}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("rectangle"));

        // 3. Move the drawing (update offsetX, offsetY)
        mockMvc.perform(put("/api/rooms/" + roomId + "/drawings/" + drawingId)
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offsetX\":45.5,\"offsetY\":-12.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetX").value(45.5))
                .andExpect(jsonPath("$.offsetY").value(-12.0));

        // 4. Retrieve drawings list
        mockMvc.perform(get("/api/rooms/" + roomId + "/drawings")
                        .with(authenticatedAs(ownerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(drawingId))
                .andExpect(jsonPath("$[0].offsetX").value(45.5));
    }

    @Test
    @DisplayName("Drawing deletion: author or room owner allowed; other member rejected (403)")
    void testDrawingDeletionPermissions() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String authorSub = "author-" + UUID.randomUUID();
        String memberSub = "member-" + UUID.randomUUID();

        // 1. Owner creates room
        String roomRes = mockMvc.perform(post("/api/rooms")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Permissions Room\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String roomId = JsonPath.read(roomRes, "$.id").toString();
        String code = JsonPath.read(roomRes, "$.invitationCode").toString();

        // 2. Author and member join
        joinRoom(authorSub, code);
        joinRoom(memberSub, code);

        // 3. Author creates drawing
        String drawingRes = mockMvc.perform(post("/api/rooms/" + roomId + "/drawings")
                        .with(authenticatedAs(authorSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"highlighter\",\"color\":\"#f3c96b\",\"width\":30.0,\"opacity\":0.35,\"payload\":\"[[50,60],[80,90]]\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String drawingId = JsonPath.read(drawingRes, "$.id").toString();

        // 4. Third-party member tries to delete -> 403 Forbidden
        mockMvc.perform(delete("/api/rooms/" + roomId + "/drawings/" + drawingId)
                        .with(authenticatedAs(memberSub)))
                .andExpect(status().isForbidden());

        // 5. Room owner CAN delete -> 204 No Content
        mockMvc.perform(delete("/api/rooms/" + roomId + "/drawings/" + drawingId)
                        .with(authenticatedAs(ownerSub)))
                .andExpect(status().isNoContent());

        // Verify drawing is deleted
        mockMvc.perform(get("/api/rooms/" + roomId + "/drawings")
                        .with(authenticatedAs(ownerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Cap of 500 drawings per room: reject 501st with 400 Bad Request")
    void testCap500Drawings() throws Exception {
        String ownerSub = "cap-owner-" + UUID.randomUUID();
        String roomIdStr = createRoom(ownerSub, "Cap Room");
        UUID roomId = UUID.fromString(roomIdStr);

        Room room = roomRepository.findById(roomId).orElseThrow();
        User owner = userRepository.findByKeycloakId(ownerSub).orElseThrow();

        // Direct DB insert of 500 drawings for speed
        for (int i = 0; i < 500; i++) {
            Drawing d = new Drawing(
                    UUID.randomUUID(),
                    room,
                    owner,
                    "pen",
                    "#273b3b",
                    5.0,
                    1.0,
                    "[[0,0]]",
                    0.0,
                    0.0,
                    Instant.now(),
                    Instant.now()
            );
            drawingRepository.save(d);
        }

        assertThat(drawingRepository.countByRoomAndDeletedFalse(room)).isEqualTo(500);

        // Attempting to create 501st drawing via REST must return 400 Bad Request
        mockMvc.perform(post("/api/rooms/" + roomIdStr + "/drawings")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"pen\",\"color\":\"#273b3b\",\"width\":5.0,\"opacity\":1.0,\"payload\":\"[[1,1]]\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Maximum drawings limit (500) reached for this room"));
    }
}
