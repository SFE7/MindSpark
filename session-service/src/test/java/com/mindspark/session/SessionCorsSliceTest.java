package com.mindspark.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import com.mindspark.session.api.SessionController;
import com.mindspark.session.config.SessionSecurityConfig;
import com.mindspark.session.security.JwtAuthConverter;
import com.mindspark.session.service.SessionSnapshotService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionController.class)
@Import({SessionSecurityConfig.class, JwtAuthConverter.class, SessionCorsSliceTest.MockConfig.class})
@TestPropertySource(properties = {
    "mindspark.cors.allowed-origins=http://localhost:4200, http://192.168.0.3:4200 "
})
class SessionCorsSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionSecurityConfig securityConfig;

    @TestConfiguration
    static class MockConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock decoder - unauthenticated");
            };
        }

        @Bean
        SessionSnapshotService sessionSnapshotService() {
            return Mockito.mock(SessionSnapshotService.class);
        }
    }

    @Test
    @DisplayName("SessionSecurityConfig binds and trims comma-separated allowed origins")
    void shouldBindAndTrimOrigins() {
        String[] origins = securityConfig.getAllowedOrigins();
        assertThat(origins).contains("http://localhost:4200", "http://127.0.0.1:4200", "http://192.168.0.3:4200");
    }

    @Test
    @DisplayName("POST /api/sessions/snapshots with Origin: http://192.168.0.3:4200 returns 401 Unauthorized (NOT 403 CORS error) and exposes Content-Disposition")
    void postWithHostIpOrigin_shouldReturn401Not403Cors() throws Exception {
        mockMvc.perform(post("/api/sessions/snapshots")
                .header("Origin", "http://192.168.0.3:4200")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"test-room-id\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
    }

    @Test
    @DisplayName("Preflight OPTIONS with Origin: http://192.168.0.3:4200 returns 200 with CORS headers")
    void optionsWithHostIpOrigin_shouldReturn200() throws Exception {
        mockMvc.perform(options("/api/sessions/snapshots")
                .header("Origin", "http://192.168.0.3:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("POST with disallowed origin is rejected with 403 Forbidden")
    void postWithDisallowedOrigin_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/sessions/snapshots")
                .header("Origin", "http://malicious-attacker.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"test-room-id\"}"))
                .andExpect(status().isForbidden());
    }
}
