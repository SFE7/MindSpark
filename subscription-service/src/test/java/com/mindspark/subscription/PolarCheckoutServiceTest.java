package com.mindspark.subscription;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspark.subscription.dto.CheckoutConfirmRequest;
import com.mindspark.subscription.dto.CheckoutRequest;
import com.mindspark.subscription.entity.BillingRecord;
import com.mindspark.subscription.entity.BillingRecordStatus;
import com.mindspark.subscription.entity.Plan;
import com.mindspark.subscription.entity.Subscription;
import com.mindspark.subscription.entity.SubscriptionStatus;
import com.mindspark.subscription.polar.client.PolarCheckoutClient;
import com.mindspark.subscription.polar.config.PolarProperties;
import com.mindspark.subscription.polar.dto.PolarCheckoutDetails;
import com.mindspark.subscription.polar.dto.PolarCheckoutResponse;
import com.mindspark.subscription.polar.exception.PolarUnavailableException;
import com.mindspark.subscription.repository.BillingRecordRepository;
import com.mindspark.subscription.repository.PlanRepository;
import com.mindspark.subscription.repository.SubscriptionRepository;
import com.mindspark.subscription.service.DatabaseInitializer;
import com.mindspark.subscription.service.KeycloakRoleSyncService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:polar_test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/mindspark",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/mindspark/protocol/openid-connect/certs",
    "mindspark.polar.api-base-url=https://sandbox-api.polar.sh",
    "mindspark.polar.access-token=mock-polar-token-xyz",
    "mindspark.polar.product-id-premium=prod_premium_123",
    "mindspark.polar.product-id-team=prod_team_456",
    "mindspark.cors.allowed-origins=http://localhost:4200,http://127.0.0.1:4200,http://192.168.0.3:4200"
})
class PolarCheckoutServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }

        @Bean
        @Primary
        PolarCheckoutClient mockPolarCheckoutClient() {
            PolarCheckoutClient client = Mockito.mock(PolarCheckoutClient.class);
            // Ensure fallback methods rethrow if called by Resilience4j AOP proxy
            lenient().when(client.createCheckoutFallback(any(), any(), any(), any(), any(), any(), any(Throwable.class)))
                    .thenAnswer(invocation -> {
                        Throwable cause = invocation.getArgument(6);
                        if (cause instanceof RuntimeException re) throw re;
                        throw new PolarUnavailableException(cause.getMessage(), cause);
                    });
            lenient().when(client.getCheckoutFallback(any(), any(Throwable.class)))
                    .thenAnswer(invocation -> {
                        Throwable cause = invocation.getArgument(1);
                        if (cause instanceof RuntimeException re) throw re;
                        throw new PolarUnavailableException(cause.getMessage(), cause);
                    });
            return client;
        }

        @Bean
        @Primary
        KeycloakRoleSyncService mockRoleSyncService() {
            KeycloakRoleSyncService sync = Mockito.mock(KeycloakRoleSyncService.class);
            lenient().when(sync.assignPremiumRole(anyString())).thenReturn(KeycloakRoleSyncService.RoleSyncStatus.SUCCESS);
            lenient().when(sync.removePremiumRole(anyString())).thenReturn(KeycloakRoleSyncService.RoleSyncStatus.SUCCESS);
            return sync;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private DatabaseInitializer databaseInitializer;

    @Autowired
    private PolarProperties polarProperties;

    @Autowired
    private PolarCheckoutClient polarCheckoutClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        billingRecordRepository.deleteAll();
        subscriptionRepository.deleteAll();
        databaseInitializer.run(null);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("polarApi");
        cb.reset();

        Mockito.reset(polarCheckoutClient);

        // Ensure fallback methods rethrow PolarUnavailableException when called by Resilience4j AOP proxy
        lenient().when(polarCheckoutClient.createCheckoutFallback(any(), any(), any(), any(), any(), any(), any(Throwable.class)))
                .thenAnswer(invocation -> {
                    Throwable cause = invocation.getArgument(6);
                    if (cause instanceof com.mindspark.subscription.polar.exception.PolarClientException pce) throw pce;
                    throw new PolarUnavailableException("Payment provider is temporarily unavailable: " + cause.getMessage(), cause);
                });
        lenient().when(polarCheckoutClient.getCheckoutFallback(any(), any(Throwable.class)))
                .thenAnswer(invocation -> {
                    Throwable cause = invocation.getArgument(1);
                    if (cause instanceof com.mindspark.subscription.polar.exception.PolarClientException pce) throw pce;
                    throw new PolarUnavailableException("Payment provider is temporarily unavailable: " + cause.getMessage(), cause);
                });
    }

    // ==========================================
    // 1. PAYMENT CONFIG
    // ==========================================

    @Test
    @DisplayName("GET /api/subscriptions/payment-config returns POLAR_SANDBOX and available plans without exposing secrets")
    void testGetPaymentConfig() throws Exception {
        mockMvc.perform(get("/api/subscriptions/payment-config")
                        .with(jwt().jwt(builder -> builder.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("POLAR_SANDBOX"))
                .andExpect(jsonPath("$.provider").value("POLAR"))
                .andExpect(jsonPath("$.availablePlans[0]").value("PREMIUM"))
                .andExpect(jsonPath("$.availablePlans[1]").value("TEAM"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.productIdPremium").doesNotExist());
    }

    // ==========================================
    // 2. CHECKOUT CREATION
    // ==========================================

    @Test
    @DisplayName("POST /api/subscriptions/checkout calls Polar and returns checkout URL for valid plan and allowed origin")
    void testCreateCheckoutSuccess() throws Exception {
        Mockito.when(polarCheckoutClient.createCheckout(
                eq("prod_premium_123"),
                eq("http://localhost:4200/billing?provider=polar&checkout_id={CHECKOUT_ID}"),
                eq("http://localhost:4200/billing?provider=polar&canceled=true"),
                eq("user-123"),
                eq("user@example.com"),
                eq("PREMIUM")
        )).thenReturn(new PolarCheckoutResponse("chk_test_123", "https://sandbox.polar.sh/checkout/chk_test_123", "open", "prod_premium_123"));

        CheckoutRequest request = new CheckoutRequest("PREMIUM", "http://localhost:4200");

        mockMvc.perform(post("/api/subscriptions/checkout")
                        .with(jwt().jwt(builder -> builder.subject("user-123").claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("POLAR_SANDBOX"))
                .andExpect(jsonPath("$.provider").value("POLAR"))
                .andExpect(jsonPath("$.checkoutId").value("chk_test_123"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://sandbox.polar.sh/checkout/chk_test_123"))
                .andExpect(jsonPath("$.planCode").value("PREMIUM"));
    }

    @Test
    @DisplayName("POST /api/subscriptions/checkout rejects disallowed returnOrigin with 400 INVALID_RETURN_ORIGIN")
    void testCreateCheckoutDisallowedOrigin() throws Exception {
        CheckoutRequest request = new CheckoutRequest("PREMIUM", "https://malicious-site.com");

        mockMvc.perform(post("/api/subscriptions/checkout")
                        .with(jwt().jwt(builder -> builder.subject("user-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RETURN_ORIGIN"));
    }

    @Test
    @DisplayName("POST /api/subscriptions/checkout falls back to SIMULATED when token is blank")
    void testCreateCheckoutSimulatedFallback() throws Exception {
        String originalToken = polarProperties.getAccessToken();
        try {
            polarProperties.setAccessToken(""); // simulate unconfigured token

            CheckoutRequest request = new CheckoutRequest("PREMIUM", "http://localhost:4200");

            mockMvc.perform(post("/api/subscriptions/checkout")
                            .with(jwt().jwt(builder -> builder.subject("user-123")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mode").value("SIMULATED"))
                    .andExpect(jsonPath("$.provider").value("SIMULATED"))
                    .andExpect(jsonPath("$.message").value("Polar sandbox not configured; use simulated upgrade."));
        } finally {
            polarProperties.setAccessToken(originalToken);
        }
    }

    // ==========================================
    // 3. CHECKOUT CONFIRMATION & IDEMPOTENCY
    // ==========================================

    @Test
    @DisplayName("POST /api/subscriptions/checkout/confirm with succeeded status activates plan and creates billing record")
    void testConfirmCheckoutSucceeded() throws Exception {
        String checkoutId = "chk_success_456";
        PolarCheckoutDetails details = new PolarCheckoutDetails(
                checkoutId,
                "succeeded",
                "prod_premium_123",
                900,
                "USD",
                "user-confirm-1",
                "confirm1@example.com",
                Map.of("user_id", "user-confirm-1", "plan_code", "PREMIUM")
        );
        Mockito.when(polarCheckoutClient.getCheckout(checkoutId)).thenReturn(details);

        CheckoutConfirmRequest request = new CheckoutConfirmRequest(checkoutId);

        // First call: Activates subscription
        mockMvc.perform(post("/api/subscriptions/checkout/confirm")
                        .with(jwt().jwt(builder -> builder.subject("user-confirm-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVATED"))
                .andExpect(jsonPath("$.alreadyProcessed").value(false))
                .andExpect(jsonPath("$.subscription.planCode").value("PREMIUM"))
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.billingRecordId").isNotEmpty());

        assertThat(subscriptionRepository.findByUserIdAndStatus("user-confirm-1", SubscriptionStatus.ACTIVE)).isPresent();
        assertThat(billingRecordRepository.findByExternalCheckoutId(checkoutId)).isPresent();

        // Second call with same checkoutId: Idempotent fast-path returns 200 with alreadyProcessed = true
        mockMvc.perform(post("/api/subscriptions/checkout/confirm")
                        .with(jwt().jwt(builder -> builder.subject("user-confirm-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVATED"))
                .andExpect(jsonPath("$.alreadyProcessed").value(true))
                .andExpect(jsonPath("$.subscription.planCode").value("PREMIUM"));

        // Verify NO duplicate billing records were created
        List<BillingRecord> records = billingRecordRepository.findByUserIdOrderByCreatedAtDesc("user-confirm-1");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getExternalCheckoutId()).isEqualTo(checkoutId);
    }

    @Test
    @DisplayName("POST /api/subscriptions/checkout/confirm with confirmed status returns 202 Accepted PENDING_CONFIRMATION")
    void testConfirmCheckoutPendingConfirmation() throws Exception {
        String checkoutId = "chk_pending_789";
        PolarCheckoutDetails details = new PolarCheckoutDetails(
                checkoutId,
                "confirmed",
                "prod_premium_123",
                900,
                "USD",
                "user-pending-1",
                "pending@example.com",
                Map.of("user_id", "user-pending-1", "plan_code", "PREMIUM")
        );
        Mockito.when(polarCheckoutClient.getCheckout(checkoutId)).thenReturn(details);

        CheckoutConfirmRequest request = new CheckoutConfirmRequest(checkoutId);

        mockMvc.perform(post("/api/subscriptions/checkout/confirm")
                        .with(jwt().jwt(builder -> builder.subject("user-pending-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.retryAfterMs").value(1500));

        // Ensure subscription was NOT activated prematurely
        assertThat(subscriptionRepository.findByUserIdAndStatus("user-pending-1", SubscriptionStatus.ACTIVE)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/subscriptions/checkout/confirm with open or expired status returns 409 PAYMENT_NOT_COMPLETED")
    void testConfirmCheckoutOpenReturns409() throws Exception {
        String checkoutId = "chk_open_999";
        PolarCheckoutDetails details = new PolarCheckoutDetails(
                checkoutId,
                "open",
                "prod_premium_123",
                900,
                "USD",
                "user-open-1",
                "open@example.com",
                Map.of("user_id", "user-open-1", "plan_code", "PREMIUM")
        );
        Mockito.when(polarCheckoutClient.getCheckout(checkoutId)).thenReturn(details);

        CheckoutConfirmRequest request = new CheckoutConfirmRequest(checkoutId);

        mockMvc.perform(post("/api/subscriptions/checkout/confirm")
                        .with(jwt().jwt(builder -> builder.subject("user-open-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_COMPLETED"))
                .andExpect(jsonPath("$.polarStatus").value("open"));
    }

    @Test
    @DisplayName("POST /api/subscriptions/checkout/confirm with mismatched user ID returns 403 CHECKOUT_OWNERSHIP_MISMATCH")
    void testConfirmCheckoutOwnershipMismatch() throws Exception {
        String checkoutId = "chk_other_user_111";
        PolarCheckoutDetails details = new PolarCheckoutDetails(
                checkoutId,
                "succeeded",
                "prod_premium_123",
                900,
                "USD",
                "legitimate-owner",
                "owner@example.com",
                Map.of("user_id", "legitimate-owner", "plan_code", "PREMIUM")
        );
        Mockito.when(polarCheckoutClient.getCheckout(checkoutId)).thenReturn(details);

        CheckoutConfirmRequest request = new CheckoutConfirmRequest(checkoutId);

        mockMvc.perform(post("/api/subscriptions/checkout/confirm")
                        .with(jwt().jwt(builder -> builder.subject("attacker-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CHECKOUT_OWNERSHIP_MISMATCH"));
    }

    // ==========================================
    // 4. RESILIENCE4J POLAR API CIRCUIT BREAKER
    // ==========================================

    @Test
    @DisplayName("Resilience4j polarApi circuit breaker trips to OPEN on repeated 5xx errors and returns 503")
    void testPolarCircuitBreakerTripsAndReturns503() throws Exception {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("polarApi");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        String checkoutId = "chk_failing_503";
        Mockito.when(polarCheckoutClient.getCheckout(checkoutId))
                .thenThrow(new PolarUnavailableException("Polar API server error: 500"));

        CheckoutConfirmRequest request = new CheckoutConfirmRequest(checkoutId);

        // Call failing endpoint to record errors in circuit breaker
        for (int i = 0; i < 6; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("Upstream 500 error"));
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Call endpoint while circuit breaker is OPEN -> returns clean 503 PAYMENT_PROVIDER_UNAVAILABLE
        mockMvc.perform(post("/api/subscriptions/checkout/confirm")
                        .with(jwt().jwt(builder -> builder.subject("user-cb-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PAYMENT_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Payment provider is temporarily unavailable. Please retry shortly."));
    }
}
