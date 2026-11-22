package com.mindspark.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import com.mindspark.media.exception.RoomForbiddenException;
import com.mindspark.media.exception.RoomNotFoundException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
@ActiveProfiles("test")
@Import(MediaCircuitBreakerTest.TestConfig.class)
public class MediaCircuitBreakerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setup() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("backendRoomAccess");
        circuitBreaker.reset();
    }

    @Test
    void circuitBreakerRegisteredAndStartsClosed() {
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void ignoredExceptionsDoNotCountTowardsFailure() {
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new RoomNotFoundException("Not found")))
                .isTrue();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new RoomForbiddenException("Forbidden")))
                .isTrue();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)))
                .isFalse();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void tripsCircuitBreakerToOpenOnRepeatedFailures() {
        for (int i = 0; i < 6; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Backend down"));
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
