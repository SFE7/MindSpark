package com.mindspark.ai.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AdminAiStatsControllerTest.TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai_stats_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema.sql",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/mindspark",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/mindspark/protocol/openid-connect/certs"
})
class AdminAiStatsControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private com.mindspark.ai.security.JwtAuthConverter jwtAuthConverter;

    private MockMvc mockMvc;

    private org.springframework.test.web.servlet.request.RequestPostProcessor withKeycloakRoles(String sub, String... roles) {
        org.springframework.security.oauth2.jwt.Jwt mockJwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(sub)
                .claim("sub", sub)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
        var authToken = jwtAuthConverter.convert(mockJwt);
        return jwt().jwt(mockJwt).authorities(authToken.getAuthorities());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock JWT Decoder");
            };
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        jdbcTemplate.execute("DELETE FROM ai_usage");
    }

    @Test
    @DisplayName("GET /api/admin/stats/ai without token returns 401 Unauthorized")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/stats/ai"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/admin/stats/ai with regular user token returns 403 Forbidden")
    void regularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/stats/ai").with(withKeycloakRoles("user-1", "user", "premium")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/stats/ai with admin token returns 200 and accurate AI metrics")
    void adminUser_returns200AndStats() throws Exception {
        LocalDate today = LocalDate.now(clock);
        LocalDate yesterday = today.minusDays(1);

        // Seed usage: user1 today: 5 calls, user2 today: 3 calls, user3 yesterday: 7 calls
        jdbcTemplate.update("INSERT INTO ai_usage (user_id, usage_day, request_count) VALUES (?, ?, ?)", "u1", today, 5);
        jdbcTemplate.update("INSERT INTO ai_usage (user_id, usage_day, request_count) VALUES (?, ?, ?)", "u2", today, 3);
        jdbcTemplate.update("INSERT INTO ai_usage (user_id, usage_day, request_count) VALUES (?, ?, ?)", "u3", yesterday, 7);

        mockMvc.perform(get("/api/admin/stats/ai").with(withKeycloakRoles("admin-1", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callsTodayUtc").value(8))
                .andExpect(jsonPath("$.totalCallsAllTime").value(15))
                .andExpect(jsonPath("$.activeAiUsersToday").value(2))
                .andExpect(jsonPath("$.tierBreakdownToday.TOTAL").value(8));
    }
}
