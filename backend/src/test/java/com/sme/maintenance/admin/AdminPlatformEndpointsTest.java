package com.sme.maintenance.admin;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.security.JwtAuthConverter;
import com.sme.maintenance.vote.Vote;
import com.sme.maintenance.vote.VoteRepository;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminPlatformEndpointsTest.TestJwtConfig.class)
public class AdminPlatformEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private VoteRepository voteRepository;

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock JWT Decoder");
            };
        }
    }

    @Autowired
    private JwtAuthConverter jwtAuthConverter;

    private RequestPostProcessor authenticatedAs(String username, List<String> roles) {
        org.springframework.security.oauth2.jwt.Jwt mockJwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("keycloak-" + username)
                .claim("preferred_username", username)
                .claim("email", username + "@example.com")
                .claim("realm_access", Map.of("roles", roles))
                .build();
        var authToken = jwtAuthConverter.convert(mockJwt);
        return jwt().jwt(mockJwt).authorities(authToken.getAuthorities());
    }

    @Test
    @DisplayName("Admin stats: Unauthenticated returns 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/stats/platform"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin stats: Regular user without admin role returns 403")
    void nonAdminUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/stats/platform")
                        .with(authenticatedAs("regular-user", List.of("user"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .with(authenticatedAs("regular-user", List.of("user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin stats: Admin role returns 200 with platform metrics and recent rooms")
    void adminUser_canFetchPlatformStats() throws Exception {
        User user = new User();
        user.setKeycloakId("kc-" + UUID.randomUUID());
        user.setUsername("roomowner");
        user.setEmail("owner-" + UUID.randomUUID() + "@example.com");
        user.setCreatedAt(new Date(System.currentTimeMillis()));
        user = userRepository.save(user);

        Room room = new Room("Admin Test Room", "ADMN001", user);
        roomRepository.save(room);

        Idea idea = new Idea(room, user, "Admin Test Idea");
        ideaRepository.save(idea);

        Vote vote = new Vote(idea, user);
        voteRepository.save(vote);

        mockMvc.perform(get("/api/admin/stats/platform")
                        .with(authenticatedAs("admin-user", List.of("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalRooms", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeRooms", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalIdeas", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalVotes", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recentRooms", notNullValue()));
    }

    @Test
    @DisplayName("Admin users directory: Admin role returns lightweight user list")
    void adminUser_canFetchUserDirectory() throws Exception {
        User user = new User();
        user.setKeycloakId("kc-dir-" + UUID.randomUUID());
        user.setUsername("diruser");
        user.setEmail("dir-" + UUID.randomUUID() + "@example.com");
        user.setCreatedAt(new Date(System.currentTimeMillis()));
        userRepository.save(user);

        mockMvc.perform(get("/api/admin/users")
                        .with(authenticatedAs("superadmin", List.of("super-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()))
                .andExpect(jsonPath("$[0].keycloakId", notNullValue()))
                .andExpect(jsonPath("$[0].email", notNullValue()))
                .andExpect(jsonPath("$[0].fullName", notNullValue()));
    }
}
