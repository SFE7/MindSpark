package com.sme.maintenance.sessionexport;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

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

import com.sme.maintenance.comment.Comment;
import com.sme.maintenance.comment.CommentRepository;
import com.sme.maintenance.entities.Category;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.repositories.CategoryRepository;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.vote.Vote;
import com.sme.maintenance.vote.VoteRepository;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InternalRoomExportControllerTest.TestJwtConfig.class)
public class InternalRoomExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private CommentRepository commentRepository;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock JWT Decoder");
            };
        }
    }

    private static final String VALID_KEY = "mindspark-internal-dev-key";

    private User createUniqueUser(String prefix) {
        String unique = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(unique);
        user.setEmail(unique + "@mindspark.test");
        user.setKeycloakId("kc-" + unique);
        user.setCreatedAt(Date.valueOf(LocalDate.now()));
        return userRepository.save(user);
    }

    @Test
    @DisplayName("GET /internal/rooms/{roomId}/export-data without X-Internal-Service-Key returns 403")
    void export_withoutKey_returns403() throws Exception {
        mockMvc.perform(get("/internal/rooms/" + UUID.randomUUID() + "/export-data")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /internal/rooms/{roomId}/export-data with wrong key returns 403")
    void export_withWrongKey_returns403() throws Exception {
        mockMvc.perform(get("/internal/rooms/" + UUID.randomUUID() + "/export-data")
                .header("X-Internal-Service-Key", "wrong-secret-key")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /internal/rooms/{roomId}/export-data for non-existent room returns 404")
    void export_unknownRoom_returns404() throws Exception {
        mockMvc.perform(get("/internal/rooms/" + UUID.randomUUID() + "/export-data")
                .header("X-Internal-Service-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /internal/rooms/{roomId}/export-data with valid key returns 200 and complete room payload")
    void export_validKey_returnsFullData() throws Exception {
        User owner = createUniqueUser("owner");
        User member = createUniqueUser("member");

        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Room room = new Room("Sprint 3 Architecture Room", code, owner);
        room.getMemberIds().add(member.getId());
        room = roomRepository.save(room);

        Category cat = new Category("Backend Architecture", room);
        cat = categoryRepository.save(cat);

        Idea idea1 = new Idea(room, owner, "Idea with low votes", "Idea Low", "Desc Low", 100.0, 100.0, "#9fc9a9", cat);
        idea1 = ideaRepository.save(idea1);

        Idea idea2 = new Idea(room, member, "Idea with high votes", "Idea High", "Desc High", 200.0, 200.0, "#ffc9a9", cat);
        idea2 = ideaRepository.save(idea2);

        // Add vote to idea2 so it ranks higher
        Vote v1 = new Vote(idea2, owner);
        voteRepository.save(v1);

        // Add comments to idea2
        Comment c1 = new Comment(idea2, owner, "Root comment on high vote idea");
        c1 = commentRepository.save(c1);

        Comment c2 = new Comment(idea2, member, "Reply to root comment", c1);
        commentRepository.save(c2);

        mockMvc.perform(get("/internal/rooms/" + room.getId() + "/export-data")
                .header("X-Internal-Service-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId", equalTo(room.getId().toString())))
                .andExpect(jsonPath("$.roomName", equalTo("Sprint 3 Architecture Room")))
                .andExpect(jsonPath("$.invitationCode", equalTo(code)))
                .andExpect(jsonPath("$.closed", equalTo(false)))
                .andExpect(jsonPath("$.ownerKeycloakId", equalTo(owner.getKeycloakId())))
                .andExpect(jsonPath("$.ownerName", equalTo(owner.getUsername())))
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].name", equalTo("Backend Architecture")))
                .andExpect(jsonPath("$.ideas", hasSize(2)))
                // Verify idea2 is first due to voteCount = 1
                .andExpect(jsonPath("$.ideas[0].id", equalTo(idea2.getId().toString())))
                .andExpect(jsonPath("$.ideas[0].voteCount", equalTo(1)))
                .andExpect(jsonPath("$.ideas[0].comments", hasSize(2)))
                .andExpect(jsonPath("$.ideas[0].comments[0].content", equalTo("Root comment on high vote idea")))
                // Verify idea1 is second due to voteCount = 0
                .andExpect(jsonPath("$.ideas[1].id", equalTo(idea1.getId().toString())))
                .andExpect(jsonPath("$.ideas[1].voteCount", equalTo(0)));
    }
}
