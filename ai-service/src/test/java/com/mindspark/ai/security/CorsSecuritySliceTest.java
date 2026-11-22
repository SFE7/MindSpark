package com.mindspark.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mindspark.ai.api.IdeaAiController;
import com.mindspark.ai.common.orchestrator.AiOrchestrator;
import com.mindspark.ai.config.AiCapsProperties;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {IdeaAiController.class})
@Import({SecurityConfig.class, JwtAuthConverter.class, CorsSecuritySliceTest.MockConfig.class})
@TestPropertySource(properties = {
    "mindspark.cors.allowed-origins=http://localhost:4200, http://192.168.0.3:4200 "
})
class CorsSecuritySliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityConfig securityConfig;

    @TestConfiguration
    static class MockConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock decoder - no token validation");
            };
        }

        @Bean
        AiOrchestrator orchestrator() {
            return Mockito.mock(AiOrchestrator.class);
        }

        @Bean
        AiCapsProperties capsProperties() {
            return new AiCapsProperties();
        }
    }

    @Test
    @DisplayName("SecurityConfig properly binds and trims comma-separated CORS origins")
    void shouldBindAndTrimOrigins() {
        String[] origins = securityConfig.getAllowedOrigins();
        assertThat(origins)
            .contains("http://localhost:4200", "http://127.0.0.1:4200", "http://192.168.0.3:4200");
    }

    @Test
    @DisplayName("POST request with Origin: http://192.168.0.3:4200 is NOT rejected with 403 Invalid CORS request")
    void postWithHostIpOrigin_shouldNotBeRejectedWith403Cors() throws Exception {
        // When unauthenticated, POST /api/ai/ideas/risks from allowed origin passes CORS filter and reaches authentication (401 Unauthorized), NOT 403 Invalid CORS request.
        mockMvc.perform(post("/api/ai/ideas/risks")
                .header("Origin", "http://192.168.0.3:4200")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"));
    }

    @Test
    @DisplayName("Preflight OPTIONS with Origin: http://192.168.0.3:4200 returns 200 with CORS headers")
    void optionsWithHostIpOrigin_shouldReturn200() throws Exception {
        mockMvc.perform(options("/api/ai/ideas/risks")
                .header("Origin", "http://192.168.0.3:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("POST request with disallowed origin IS rejected with 403 Forbidden")
    void postWithDisallowedOrigin_shouldBeRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/ai/ideas/risks")
                .header("Origin", "http://malicious-site.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
