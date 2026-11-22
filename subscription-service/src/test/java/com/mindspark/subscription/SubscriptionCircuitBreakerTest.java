package com.mindspark.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
@ActiveProfiles("test")
@Import(SubscriptionCircuitBreakerTest.TestConfig.class)
public class SubscriptionCircuitBreakerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setup() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("keycloakAdmin");
        cb.reset();
    }

    @Test
    void keycloakAdminCircuitBreakerRegisteredAndStartsClosed() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("keycloakAdmin");
        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void polarApiCircuitBreakerRegisteredAndConfiguredForPhase7B() {
        CircuitBreaker polarCb = circuitBreakerRegistry.circuitBreaker("polarApi");
        assertThat(polarCb).isNotNull();
        assertThat(polarCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        CircuitBreakerConfig config = polarCb.getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(20000L);
    }

    @Test
    void tripsKeycloakAdminCircuitBreakerOnRepeatedFailures() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("keycloakAdmin");

        for (int i = 0; i < 6; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("Keycloak down"));
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
