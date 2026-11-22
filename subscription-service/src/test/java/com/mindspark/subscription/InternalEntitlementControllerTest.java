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

import com.mindspark.subscription.api.InternalEntitlementController;
import com.mindspark.subscription.config.SubscriptionSecurityConfig;
import com.mindspark.subscription.dto.EntitlementResponse;
import com.mindspark.subscription.security.JwtAuthConverter;
import com.mindspark.subscription.service.SubscriptionService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InternalEntitlementController.class})
@Import({SubscriptionSecurityConfig.class, JwtAuthConverter.class, InternalEntitlementControllerTest.MockConfig.class})
@TestPropertySource(properties = {
    "mindspark.internal.service-secret=test-secret-key-12345"
})
class InternalEntitlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionService subscriptionService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mock decoder");
            };
        }

        @Bean
        SubscriptionService subscriptionService() {
            return Mockito.mock(SubscriptionService.class);
        }
    }

    @Test
    @DisplayName("GET /internal/entitlements/{userId} without X-Internal-Service-Key returns 403 Forbidden")
    void getEntitlements_withoutKey_returns403() throws Exception {
        mockMvc.perform(get("/internal/entitlements/user-123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /internal/entitlements/{userId} with invalid X-Internal-Service-Key returns 403 Forbidden")
    void getEntitlements_withInvalidKey_returns403() throws Exception {
        mockMvc.perform(get("/internal/entitlements/user-123")
                .header("X-Internal-Service-Key", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /internal/entitlements/{userId} with valid key returns 200 and entitlements")
    void getEntitlements_withValidKey_returns200() throws Exception {
        when(subscriptionService.getEffectiveEntitlement(eq("user-123")))
                .thenReturn(new EntitlementResponse("user-123", "FREE", "Free", "FREE", 3, 5, 10));

        mockMvc.perform(get("/internal/entitlements/user-123")
                .header("X-Internal-Service-Key", "test-secret-key-12345")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.planCode").value("FREE"))
                .andExpect(jsonPath("$.maxActiveRooms").value(3))
                .andExpect(jsonPath("$.maxParticipantsPerRoom").value(5))
                .andExpect(jsonPath("$.aiDailyLimit").value(10));
    }
}
