package com.mindspark.ai.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.quota.QuotaService;
import com.mindspark.ai.security.JwtAuthConverter;
import com.mindspark.ai.security.SecurityConfig;
import com.mindspark.ai.tier.Tier;
import com.mindspark.ai.tier.TierResolver;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, AiUserController.class})
@Import({SecurityConfig.class, JwtAuthConverter.class, TierResolver.class, AiEndpointsTest.TestSecurityConfig.class})
class AiEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthConverter jwtAuthConverter;

    @Autowired
    private TierResolver tierResolver;

    @Autowired
    private QuotaService quotaService;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Mocked decoder - not used when JWT post processor is active");
            };
        }

        @Bean
        AiTiersProperties aiTiersProperties() {
            return new AiTiersProperties();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        QuotaService quotaService() {
            return Mockito.mock(QuotaService.class);
        }
    }

    @Test
    @DisplayName("GET /api/ai/health is public and returns OK without authentication")
    void testHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/ai/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("GET /api/ai/me returns 401 Unauthorized without token")
    void testMeEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/ai/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/ai/me returns userId and PREMIUM tier with premium role in JWT")
    void testMeEndpointWithPremiumRoleReturnsPremiumTier() throws Exception {
        mockMvc.perform(get("/api/ai/me")
                .with(jwt()
                        .jwt(token -> token.subject("user-premium-123"))
                        .authorities(new SimpleGrantedAuthority("ROLE_premium"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-premium-123"))
                .andExpect(jsonPath("$.tier").value("PREMIUM"));
    }

    @Test
    @DisplayName("GET /api/ai/me returns userId and FREE tier with only free role in JWT")
    void testMeEndpointWithFreeRoleReturnsFreeTier() throws Exception {
        mockMvc.perform(get("/api/ai/me")
                .with(jwt()
                        .jwt(token -> token.subject("user-free-456"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-free-456"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    @DisplayName("GET /api/ai/me returns userId and FREE tier when JWT has no roles")
    void testMeEndpointWithNoRolesReturnsFreeTier() throws Exception {
        mockMvc.perform(get("/api/ai/me")
                .with(jwt()
                        .jwt(token -> token.subject("user-norole-789"))
                        .authorities(Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-norole-789"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    @DisplayName("GET /api/ai/usage returns 401 Unauthorized without token")
    void testUsageEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/ai/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/ai/usage returns FREE tier limits and calculated remaining count")
    void testUsageEndpointForFreeUser() throws Exception {
        Mockito.when(quotaService.getUsageToday("user-free-456")).thenReturn(3);

        mockMvc.perform(get("/api/ai/usage")
                .with(jwt()
                        .jwt(token -> token.subject("user-free-456"))
                        .authorities(new SimpleGrantedAuthority("ROLE_free"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.dailyLimit").value(10))
                .andExpect(jsonPath("$.usedToday").value(3))
                .andExpect(jsonPath("$.remainingToday").value(7))
                .andExpect(jsonPath("$.resetsAtUtc").value("2026-11-20T00:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/ai/usage returns PREMIUM tier limits and calculated remaining count")
    void testUsageEndpointForPremiumUser() throws Exception {
        Mockito.when(quotaService.getUsageToday("user-prem-789")).thenReturn(5);

        mockMvc.perform(get("/api/ai/usage")
                .with(jwt()
                        .jwt(token -> token.subject("user-prem-789"))
                        .authorities(new SimpleGrantedAuthority("ROLE_premium"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.dailyLimit").value(20))
                .andExpect(jsonPath("$.usedToday").value(5))
                .andExpect(jsonPath("$.remainingToday").value(15))
                .andExpect(jsonPath("$.resetsAtUtc").value("2026-11-20T00:00:00Z"));
    }

    @Test
    @DisplayName("End-to-end converter & resolver: Jwt with realm_access.roles: ['premium'] maps to ROLE_premium and resolves to PREMIUM")
    void testConverterEndToEndWithPremiumJwt() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-converter-123")
                .claim("preferred_username", "premium_user")
                .claim("realm_access", Map.of("roles", List.of("premium")))
                .build();

        AbstractAuthenticationToken auth = jwtAuthConverter.convert(jwt);
        Tier tier = tierResolver.resolve(auth.getAuthorities());
        assertEquals(Tier.PREMIUM, tier);
    }

    @Test
    @DisplayName("End-to-end converter & resolver: Jwt with realm_access.roles: ['free'] maps to ROLE_free and resolves to FREE")
    void testConverterEndToEndWithFreeJwt() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-converter-456")
                .claim("preferred_username", "free_user")
                .claim("realm_access", Map.of("roles", List.of("free")))
                .build();

        AbstractAuthenticationToken auth = jwtAuthConverter.convert(jwt);
        Tier tier = tierResolver.resolve(auth.getAuthorities());
        assertEquals(Tier.FREE, tier);
    }
}
