package com.mindspark.ai.common.orchestrator;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.mindspark.ai.common.dto.AiResponse;
import com.mindspark.ai.common.exception.DailyLimitReachedException;
import com.mindspark.ai.common.exception.ValidationException;
import com.mindspark.ai.common.util.StructuredOutputHelper;
import com.mindspark.ai.config.AiCapsProperties;
import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.provider.exception.AllProvidersFailed;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.router.ModelRouter;
import com.mindspark.ai.quota.QuotaService;
import com.mindspark.ai.tier.Tier;
import com.mindspark.ai.tier.TierResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AiOrchestrator {

    private final TierResolver tierResolver;
    private final AiTiersProperties tiersProperties;
    private final AiCapsProperties capsProperties;
    private final QuotaService quotaService;
    private final ModelRouter modelRouter;
    private final StructuredOutputHelper structuredOutputHelper;

    private final Semaphore concurrencyLimiter;
    private final int maxConcurrentCalls;
    private final long acquireTimeoutMs;
    private final long retryAfterSeconds;

    public AiOrchestrator(
            TierResolver tierResolver,
            AiTiersProperties tiersProperties,
            AiCapsProperties capsProperties,
            QuotaService quotaService,
            ModelRouter modelRouter,
            StructuredOutputHelper structuredOutputHelper) {
        this(tierResolver, tiersProperties, capsProperties, quotaService, modelRouter, structuredOutputHelper, 8, 500, 60);
    }

    @Autowired
    public AiOrchestrator(
            TierResolver tierResolver,
            AiTiersProperties tiersProperties,
            AiCapsProperties capsProperties,
            QuotaService quotaService,
            ModelRouter modelRouter,
            StructuredOutputHelper structuredOutputHelper,
            @Value("${mindspark.ai.max-concurrent-upstream-calls:8}") int maxConcurrentCalls,
            @Value("${mindspark.ai.concurrency-acquire-timeout-ms:500}") long acquireTimeoutMs,
            @Value("${mindspark.ai.concurrency-retry-after-seconds:60}") long retryAfterSeconds) {
        this.tierResolver = tierResolver;
        this.tiersProperties = tiersProperties;
        this.capsProperties = capsProperties;
        this.quotaService = quotaService;
        this.modelRouter = modelRouter;
        this.structuredOutputHelper = structuredOutputHelper;
        this.maxConcurrentCalls = maxConcurrentCalls > 0 ? maxConcurrentCalls : 8;
        this.acquireTimeoutMs = acquireTimeoutMs >= 0 ? acquireTimeoutMs : 500;
        this.retryAfterSeconds = retryAfterSeconds > 0 ? retryAfterSeconds : 60;
        this.concurrencyLimiter = new Semaphore(this.maxConcurrentCalls, true);
    }

    public <T> AiResponse<T> execute(
            Authentication authentication,
            int payloadCharCount,
            ChatRequest chatRequest,
            Class<T> resultClass,
            Consumer<T> validator) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ValidationException("Authentication is required");
        }

        String userId = extractUserId(authentication);
        Tier tier = tierResolver.resolve(authentication.getAuthorities());

        if (payloadCharCount > capsProperties.getMaxPayloadChars()) {
            throw new ValidationException("Total request payload exceeds maximum allowed size of "
                    + capsProperties.getMaxPayloadChars() + " characters");
        }

        int limit = tiersProperties.getTierConfig(tier).getDailyRequestLimit();
        boolean reserved = quotaService.reserve(userId, limit);
        if (!reserved) {
            throw new DailyLimitReachedException(limit);
        }

        boolean acquired = false;
        try {
            try {
                acquired = concurrencyLimiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                acquired = false;
            }
            if (!acquired) {
                log.warn("Global AI provider concurrency limit ({}) reached, rejecting request for user '{}'",
                        maxConcurrentCalls, userId);
                throw new AllProvidersFailed("The AI provider is currently busy. Please try again in a minute.", true, retryAfterSeconds);
            }

            ParsedWithModel<T> parsed = modelRouter.execute(tier, chatRequest, chatResult -> {
                T obj = structuredOutputHelper.parseAndValidate(chatResult.text(), resultClass, validator);
                return new ParsedWithModel<>(obj, chatResult.modelUsed());
            });

            return new AiResponse<>(parsed.result(), new AiResponse.Meta(parsed.modelUsed(), tier.name()));

        } catch (Exception e) {
            log.warn("AI processing failed for user '{}' on tier {}: {}. Refunding quota.",
                    userId != null ? userId.substring(0, Math.min(8, userId.length())) + "..." : "unknown",
                    tier,
                    e.getMessage());
            quotaService.refund(userId);
            throw e;
        } finally {
            if (acquired) {
                concurrencyLimiter.release();
            }
        }
    }

    private String extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return jwtToken.getToken().getSubject();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else {
            return authentication.getName();
        }
    }

    private record ParsedWithModel<T>(T result, String modelUsed) {}
}
