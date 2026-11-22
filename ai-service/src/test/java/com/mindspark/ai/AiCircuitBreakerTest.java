package com.mindspark.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import com.mindspark.ai.common.exception.DailyLimitReachedException;
import com.mindspark.ai.common.exception.ValidationException;
import com.mindspark.ai.provider.client.LlmClientFactory;
import com.mindspark.ai.provider.exception.AllProvidersFailed;
import com.mindspark.ai.provider.exception.ModelUnavailable;
import com.mindspark.ai.provider.exception.ProviderBadRequest;
import com.mindspark.ai.provider.exception.ProviderUnavailable;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.router.ModelRouter;
import com.mindspark.ai.tier.Tier;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
@ActiveProfiles("test")
@Import(AiCircuitBreakerTest.TestConfig.class)
public class AiCircuitBreakerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        LlmClientFactory mockClientFactory() {
            return (model, tier) -> new com.mindspark.ai.provider.client.LlmClient() {
                @Override
                public com.mindspark.ai.provider.model.ChatResult complete(ChatRequest request) {
                    throw new ProviderUnavailable("AI upstream down");
                }
            };
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private ModelRouter modelRouter;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setup() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiExternalProvider");
        circuitBreaker.reset();
    }

    @Test
    void circuitBreakerRegisteredAndStartsClosed() {
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void tripsCircuitBreakerToOpenOnRepeatedFailures() {
        ChatRequest request = ChatRequest.builder().systemPrompt("test").userPrompt("ping").build();

        // Minimum calls is 5, failure threshold is 50%
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> modelRouter.execute(Tier.FREE, request, Function.identity()))
                    .isInstanceOf(AllProvidersFailed.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call is rejected by open circuit breaker and wrapped cleanly
        assertThatThrownBy(() -> modelRouter.execute(Tier.FREE, request, Function.identity()))
                .isInstanceOf(AllProvidersFailed.class);
    }

    @Test
    void ignoredExceptionsDoNotTripCircuitBreaker() {
        // Assert that client-side and configuration exceptions are in the ignoreExceptionPredicate
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new ValidationException("Invalid")))
                .isTrue();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new DailyLimitReachedException(10)))
                .isTrue();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new ProviderBadRequest("Bad request")))
                .isTrue();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new ModelUnavailable("No model")))
                .isTrue();

        // Assert that provider server errors are NOT ignored
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new ProviderUnavailable("Down")))
                .isFalse();
        assertThat(circuitBreaker.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(new AllProvidersFailed("All down", false, null)))
                .isFalse();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
