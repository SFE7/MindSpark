package com.mindspark.ai.provider.router;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.provider.client.LlmClient;
import com.mindspark.ai.provider.client.LlmClientFactory;
import com.mindspark.ai.provider.exception.AllProvidersFailed;
import com.mindspark.ai.provider.exception.InvalidModelOutputException;
import com.mindspark.ai.provider.exception.ModelUnavailable;
import com.mindspark.ai.provider.exception.ProviderBadRequest;
import com.mindspark.ai.provider.exception.ProviderException;
import com.mindspark.ai.provider.exception.ProviderRateLimited;
import com.mindspark.ai.provider.exception.ProviderUnavailable;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.model.ChatResult;
import com.mindspark.ai.tier.Tier;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ModelRouter {

    private final AiTiersProperties properties;
    private final LlmClientFactory clientFactory;

    public ModelRouter(AiTiersProperties properties, LlmClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @CircuitBreaker(name = "aiExternalProvider", fallbackMethod = "executeFallbackChatResult")
    public ChatResult execute(Tier tier, ChatRequest request) {
        return execute(tier, request, Function.identity());
    }

    public ChatResult executeFallbackChatResult(Tier tier, ChatRequest request, Throwable t) {
        log.warn("Circuit breaker aiExternalProvider fallback triggered for tier {}: {}", tier, t.getMessage());
        if (t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException cnpe) {
            throw new AllProvidersFailed("AI providers are temporarily unavailable (circuit breaker open).", false, null, cnpe);
        }
        if (t instanceof RuntimeException re) {
            throw re;
        }
        throw new AllProvidersFailed("AI providers are temporarily unavailable (circuit breaker fallback).", false, null, t);
    }

    @CircuitBreaker(name = "aiExternalProvider", fallbackMethod = "executeFallback")
    public <T> T execute(Tier tier, ChatRequest request, Function<ChatResult, T> parser) {
        List<String> models = properties.getTierConfig(tier).getModels();
        if (models == null || models.isEmpty()) {
            throw new AllProvidersFailed("No models configured for tier " + tier, false, null);
        }

        List<Throwable> failures = new ArrayList<>();
        long deadlineMillis = System.currentTimeMillis() + (properties.getTotalTimeoutSeconds() * 1000L);

        for (String model : models) {
            long remainingMillis = deadlineMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                log.warn("Total timeout deadline exceeded ({}s) before attempting model '{}'",
                        properties.getTotalTimeoutSeconds(), model);
                Throwable lastFailure = failures.isEmpty() ? null : failures.get(failures.size() - 1);
                throw new AllProvidersFailed("Timeout budget exceeded before attempting model " + model, false, null, lastFailure);
            }

            long perAttemptCapMillis = (properties.getPerAttemptTimeoutSeconds() > 0 ? properties.getPerAttemptTimeoutSeconds() : properties.getReadTimeoutSeconds()) * 1000L;
            long effectiveReadTimeoutMillis = Math.min(perAttemptCapMillis, remainingMillis);
            Duration callReadTimeout = Duration.ofMillis(effectiveReadTimeoutMillis);

            try {
                log.info("Attempting completion with model '{}' for tier {} (timeout={}ms)", model, tier, effectiveReadTimeoutMillis);
                LlmClient client = clientFactory.getClient(model, tier);
                ChatResult result = client.complete(request, callReadTimeout);
                return parser.apply(result);
            } catch (ProviderRateLimited e) {
                log.warn("Model '{}' was rate-limited (retryAfter={}), falling back to next model",
                        model, e.getRetryAfterSeconds());
                failures.add(e);
            } catch (ModelUnavailable e) {
                log.warn("Model '{}' is unavailable (wrong model ID or deprecated), falling back to next model", model);
                failures.add(e);
            } catch (ProviderBadRequest e) {
                log.warn("Provider rejected request for model '{}' (bad request / generation failed), falling back to next model", model);
                failures.add(e);
            } catch (ProviderUnavailable e) {
                log.warn("Provider unavailable for model '{}', falling back to next model", model);
                failures.add(e);
            } catch (InvalidModelOutputException e) {
                log.warn("Invalid model output from model '{}', falling back to next model", model);
                failures.add(e);
            } catch (Exception e) {
                log.warn("Unexpected failure from model '{}', falling back to next model", model);
                failures.add(e);
            }
        }

        boolean allRateLimited = !failures.isEmpty() && failures.stream().allMatch(f -> f instanceof ProviderRateLimited);
        boolean anyRateLimited = failures.stream().anyMatch(f -> f instanceof ProviderRateLimited);

        Long minRetryAfter = failures.stream()
                .filter(f -> f instanceof ProviderRateLimited)
                .map(f -> ((ProviderRateLimited) f).getRetryAfterSeconds())
                .filter(Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null);

        Throwable lastFailure = failures.isEmpty() ? null : failures.get(failures.size() - 1);

        if (allRateLimited || (anyRateLimited && failures.size() == 1)) {
            throw new AllProvidersFailed("All AI providers are currently rate-limited", true, minRetryAfter, lastFailure);
        }

        boolean allInvalidOutput = !failures.isEmpty() && failures.stream().allMatch(f -> f instanceof InvalidModelOutputException);
        if (allInvalidOutput) {
            throw new InvalidModelOutputException("All models produced invalid or unparseable output", lastFailure);
        }

        throw new AllProvidersFailed("All configured AI providers failed for tier " + tier, false, null, lastFailure);
    }

    public <T> T executeFallback(Tier tier, ChatRequest request, Function<ChatResult, T> parser, Throwable t) {
        log.warn("Circuit breaker aiExternalProvider fallback triggered for tier {}: {}", tier, t.getMessage());
        if (t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException cnpe) {
            throw new AllProvidersFailed("AI providers are temporarily unavailable (circuit breaker open).", false, null, cnpe);
        }
        if (t instanceof RuntimeException re) {
            throw re;
        }
        throw new AllProvidersFailed("AI providers are temporarily unavailable (circuit breaker fallback).", false, null, t);
    }
}
