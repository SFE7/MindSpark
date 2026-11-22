package com.mindspark.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mindspark.media.api.MediaController;
import com.mindspark.media.client.BackendRoomClient;
import com.mindspark.media.config.MediaSecurityConfig;
import com.mindspark.media.security.JwtAuthConverter;
import com.mindspark.media.service.MediaStorageService;
import com.mindspark.media.validation.ImageBinaryValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@Import({MediaSecurityConfig.class, JwtAuthConverter.class, MediaCorsSliceTest.MockConfig.class})
@TestPropertySource(properties = {
        "mindspark.cors.allowed-origins=http://localhost:4200, http://192.168.0.3:4200 "
})
class MediaCorsSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaSecurityConfig securityConfig;

    @TestConfiguration
    static class MockConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock decoder - unauthenticated");
            };
        }

        @Bean
        BackendRoomClient backendRoomClient() {
            return Mockito.mock(BackendRoomClient.class);
        }

        @Bean
        ImageBinaryValidator imageBinaryValidator() {
            return Mockito.mock(ImageBinaryValidator.class);
        }

        @Bean
        MediaStorageService mediaStorageService() {
            return Mockito.mock(MediaStorageService.class);
        }
    }

    @Test
    @DisplayName("MediaSecurityConfig binds and trims comma-separated allowed origins")
    void shouldBindAndTrimOrigins() {
        String[] origins = securityConfig.getAllowedOrigins();
        assertThat(origins).contains("http://localhost:4200", "http://127.0.0.1:4200", "http://192.168.0.3:4200");
    }

    @Test
    @DisplayName("POST /api/media/rooms/test-room with Origin: http://192.168.0.3:4200 returns 401 Unauthorized (NOT 403 CORS error)")
    void postWithHostIpOrigin_shouldReturn401Not403Cors() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/media/rooms/test-room")
                        .file(file)
                        .header("Origin", "http://192.168.0.3:4200"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"));
    }

    @Test
    @DisplayName("Preflight OPTIONS with Origin: http://192.168.0.3:4200 returns 200 with CORS headers")
    void optionsWithHostIpOrigin_shouldReturn200() throws Exception {
        mockMvc.perform(options("/api/media/rooms/test-room")
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
        mockMvc.perform(post("/api/media/rooms/test-room")
                        .header("Origin", "http://malicious-attacker.com")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
