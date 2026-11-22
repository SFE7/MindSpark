package com.mindspark.subscription;

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

import com.mindspark.subscription.api.AdminPlanController;
import com.mindspark.subscription.api.SubscriptionController;
import com.mindspark.subscription.config.SubscriptionSecurityConfig;
import com.mindspark.subscription.security.JwtAuthConverter;
import com.mindspark.subscription.service.AdminSubscriptionService;
import com.mindspark.subscription.service.SubscriptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

@WebMvcTest(controllers = {SubscriptionController.class, AdminPlanController.class})
@Import({SubscriptionSecurityConfig.class, JwtAuthConverter.class, SubscriptionCorsSliceTest.MockConfig.class})
@TestPropertySource(properties = {
    "mindspark.cors.allowed-origins=http://localhost:4200, http://192.168.0.3:4200 "
})
class SubscriptionCorsSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionSecurityConfig securityConfig;

    @TestConfiguration
    static class MockConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock decoder - unauthenticated");
            };
        }

        @Bean
        SubscriptionService subscriptionService() {
            return Mockito.mock(SubscriptionService.class);
        }

        @Bean
        AdminSubscriptionService adminSubscriptionService() {
            return Mockito.mock(AdminSubscriptionService.class);
        }
    }

    @Test
    @DisplayName("SubscriptionSecurityConfig binds and trims comma-separated allowed origins")
    void shouldBindAndTrimOrigins() {
        String[] origins = securityConfig.getAllowedOrigins();
        assertThat(origins).contains("http://localhost:4200", "http://127.0.0.1:4200", "http://192.168.0.3:4200");
    }

    @Test
    @DisplayName("POST /api/subscriptions/subscribe with Origin: http://192.168.0.3:4200 returns 401 Unauthorized (NOT 403 CORS error)")
    void postWithHostIpOrigin_shouldReturn401Not403Cors() throws Exception {
        mockMvc.perform(post("/api/subscriptions/subscribe")
                .header("Origin", "http://192.168.0.3:4200")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PREMIUM\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"));
    }

    @Test
    @DisplayName("PUT /api/admin/plans/{id} with Origin: http://192.168.0.3:4200 returns 401 Unauthorized (NOT 403 CORS error)")
    void putAdminPlansWithHostIpOrigin_shouldReturn401Not403Cors() throws Exception {
        mockMvc.perform(put("/api/admin/plans/" + UUID.randomUUID())
                .header("Origin", "http://192.168.0.3:4200")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"priceCents\":1000,\"currency\":\"USD\",\"billingInterval\":\"MONTHLY\",\"maxActiveRooms\":5,\"maxParticipantsPerRoom\":10,\"aiDailyLimit\":20,\"aiTier\":\"PREMIUM\",\"active\":true,\"sortOrder\":2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.0.3:4200"));
    }

    @Test
    @DisplayName("Preflight OPTIONS with Origin: http://192.168.0.3:4200 returns 200 with CORS headers")
    void optionsWithHostIpOrigin_shouldReturn200() throws Exception {
        mockMvc.perform(options("/api/subscriptions/subscribe")
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
        mockMvc.perform(post("/api/subscriptions/subscribe")
                .header("Origin", "http://malicious-site.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PREMIUM\"}"))
                .andExpect(status().isForbidden());
    }
}
