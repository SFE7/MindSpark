package com.sme.maintenance.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(SubscriptionEntitlementCircuitBreakerTest.TestConfig.class)
@TestPropertySource(properties = {
        "subscription.service.url=http://127.0.0.1:59998",
        "mindspark.subscription.fallback-enabled=false",
        "resilience4j.circuitbreaker.instances.subscriptionEntitlement.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.subscriptionEntitlement.minimum-number-of-calls=5",
        "resilience4j.circuitbreaker.instances.subscriptionEntitlement.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.subscriptionEntitlement.wait-duration-in-open-state=30s"
})
class SubscriptionEntitlementCircuitBreakerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }
    }

    @Autowired
    private EntitlementClient entitlementClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("Verify subscriptionEntitlement circuit breaker transitions CLOSED -> OPEN on consecutive failures")
    void testCircuitBreakerTransitionsClosedToOpen() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("subscriptionEntitlement");
        assertThat(cb).isNotNull();
        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Execute 5 failing calls to satisfy minimum-number-of-calls and trip 100% failure rate
        for (int i = 0; i < 5; i++) {
            final String sub = "test-sub-" + i;
            assertThatThrownBy(() -> entitlementClient.getEntitlements(sub))
                    .isInstanceOf(SubscriptionServiceUnavailableException.class);
        }

        // Circuit breaker should now transition to OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call should immediately trigger fallback via circuit breaker being OPEN
        assertThatThrownBy(() -> entitlementClient.getEntitlements("test-sub-blocked"))
                .isInstanceOf(SubscriptionServiceUnavailableException.class);

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        cb.reset();
    }
}
