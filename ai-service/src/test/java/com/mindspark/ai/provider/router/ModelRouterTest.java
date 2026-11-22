package com.mindspark.ai.provider.router;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.provider.client.LlmClient;
import com.mindspark.ai.provider.client.LlmClientFactory;
import com.mindspark.ai.provider.exception.AllProvidersFailed;
import com.mindspark.ai.provider.exception.InvalidModelOutputException;
import com.mindspark.ai.provider.exception.ModelUnavailable;
import com.mindspark.ai.provider.exception.ProviderBadRequest;
import com.mindspark.ai.provider.exception.ProviderRateLimited;
import com.mindspark.ai.provider.exception.ProviderUnavailable;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.model.ChatResult;
import com.mindspark.ai.tier.Tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRouterTest {

    private AiTiersProperties properties;
    private Map<String, LlmClient> fakeClients;
    private LlmClientFactory fakeFactory;
    private ModelRouter modelRouter;

    @BeforeEach
    void setUp() {
        properties = new AiTiersProperties();
        fakeClients = new HashMap<>();
        fakeFactory = (model, tier) -> {
            LlmClient client = fakeClients.get(model);
            if (client == null) {
                throw new IllegalStateException("No fake client configured for model: " + model);
            }
            return client;
        };
        modelRouter = new ModelRouter(properties, fakeFactory);
    }

    @Test
    @DisplayName("Primary model succeeds: returns first model result without calling fallback")
    void testPrimaryModelSucceeds() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> new ChatResult("Idea 1", "gemini-3.5-flash-lite", null));
        fakeClients.put("gemini-3.1-flash-lite", req -> {
            throw new AssertionError("Fallback should not be called when primary succeeds");
        });

        ChatResult result = modelRouter.execute(Tier.FREE, request);

        assertNotNull(result);
        assertEquals("Idea 1", result.text());
        assertEquals("gemini-3.5-flash-lite", result.modelUsed());
    }

    @Test
    @DisplayName("Primary model rate limited (429): falls back to second model")
    void testPrimaryRateLimitedFallsBackToSecond() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> {
            throw new ProviderRateLimited("Rate limit hit", 30L);
        });
        fakeClients.put("gemini-3.1-flash-lite", req -> new ChatResult("Fallback idea", "gemini-3.1-flash-lite", null));

        ChatResult result = modelRouter.execute(Tier.FREE, request);

        assertNotNull(result);
        assertEquals("Fallback idea", result.text());
        assertEquals("gemini-3.1-flash-lite", result.modelUsed());
    }

    @Test
    @DisplayName("Primary model unavailable (404/400): falls back to second model")
    void testPrimaryUnavailableFallsBackToSecond() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> {
            throw new ModelUnavailable("Model not found");
        });
        fakeClients.put("gemini-3.1-flash-lite", req -> new ChatResult("Fallback from 404", "gemini-3.1-flash-lite", null));

        ChatResult result = modelRouter.execute(Tier.FREE, request);

        assertNotNull(result);
        assertEquals("Fallback from 404", result.text());
        assertEquals("gemini-3.1-flash-lite", result.modelUsed());
    }

    @Test
    @DisplayName("Primary model returns ProviderBadRequest (400): falls back to second model")
    void testPrimaryBadRequestFallsBackToSecond() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> {
            throw new ProviderBadRequest("Bad request: json_validate_failed");
        });
        fakeClients.put("gemini-3.1-flash-lite", req -> new ChatResult("Fallback from 400", "gemini-3.1-flash-lite", null));

        ChatResult result = modelRouter.execute(Tier.FREE, request);

        assertNotNull(result);
        assertEquals("Fallback from 400", result.text());
        assertEquals("gemini-3.1-flash-lite", result.modelUsed());
    }

    @Test
    @DisplayName("Total timeout budget passed: throws AllProvidersFailed without calling further models")
    void testTotalDeadlineBudgetExceededThrowsAllProvidersFailed() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        properties.setTotalTimeoutSeconds(0); // deadline immediately expired

        fakeClients.put("gemini-3.5-flash-lite", req -> new ChatResult("Not reached", "gemini-3.5-flash-lite", null));
        fakeClients.put("gemini-3.1-flash-lite", req -> new ChatResult("Not reached", "gemini-3.1-flash-lite", null));

        AllProvidersFailed ex = assertThrows(AllProvidersFailed.class, () -> modelRouter.execute(Tier.FREE, request));
        assertTrue(ex.getMessage().contains("Timeout budget exceeded"));
    }

    @Test
    @DisplayName("Per-attempt cap enforces bounded timeout on slow primary, allowing second model to succeed")
    void testPerAttemptTimeoutEnforcedForSlowPrimary() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        properties.setTotalTimeoutSeconds(50);
        properties.setPerAttemptTimeoutSeconds(25);

        java.util.concurrent.atomic.AtomicReference<java.time.Duration> primaryTimeout = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<java.time.Duration> secondaryTimeout = new java.util.concurrent.atomic.AtomicReference<>();

        fakeClients.put("gemini-3.5-flash-lite", new LlmClient() {
            @Override
            public ChatResult complete(ChatRequest req) {
                return null;
            }

            @Override
            public ChatResult complete(ChatRequest req, java.time.Duration timeout) {
                primaryTimeout.set(timeout);
                throw new ProviderUnavailable("Primary timed out after 25s");
            }
        });

        fakeClients.put("gemini-3.1-flash-lite", new LlmClient() {
            @Override
            public ChatResult complete(ChatRequest req) {
                return null;
            }

            @Override
            public ChatResult complete(ChatRequest req, java.time.Duration timeout) {
                secondaryTimeout.set(timeout);
                return new ChatResult("Secondary succeeded", "gemini-3.1-flash-lite", null);
            }
        });

        ChatResult result = modelRouter.execute(Tier.FREE, request);

        assertNotNull(result);
        assertEquals("Secondary succeeded", result.text());
        assertNotNull(primaryTimeout.get());
        assertTrue(primaryTimeout.get().toMillis() <= 25000L);
        assertNotNull(secondaryTimeout.get());
        assertTrue(secondaryTimeout.get().toMillis() <= 25000L);
    }

    @Test
    @DisplayName("All models fail with rate limits: throws AllProvidersFailed carrying rateLimited=true and retryAfter")
    void testAllModelsRateLimitedThrowsAllProvidersFailed() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> {
            throw new ProviderRateLimited("Rate limit 1", 60L);
        });
        fakeClients.put("gemini-3.1-flash-lite", req -> {
            throw new ProviderRateLimited("Rate limit 2", 20L);
        });

        AllProvidersFailed ex = assertThrows(AllProvidersFailed.class, () -> modelRouter.execute(Tier.FREE, request));

        assertTrue(ex.isRateLimited());
        assertEquals(20L, ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("All models fail with server/network errors: throws AllProvidersFailed with rateLimited=false")
    void testAllModelsUnavailableThrowsAllProvidersFailed() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> {
            throw new ProviderUnavailable("503 server error");
        });
        fakeClients.put("gemini-3.1-flash-lite", req -> {
            throw new ModelUnavailable("Model 404 not found");
        });

        AllProvidersFailed ex = assertThrows(AllProvidersFailed.class, () -> modelRouter.execute(Tier.FREE, request));

        assertFalse(ex.isRateLimited());
    }

    @Test
    @DisplayName("Invalid model output on primary falls back to second model")
    void testInvalidModelOutputFallsBack() {
        ChatRequest request = ChatRequest.builder().userPrompt("Generate ideas").build();

        fakeClients.put("gemini-3.5-flash-lite", req -> new ChatResult("bad json {broken", "gemini-3.5-flash-lite", null));
        fakeClients.put("gemini-3.1-flash-lite", req -> new ChatResult("valid output", "gemini-3.1-flash-lite", null));

        String parsed = modelRouter.execute(Tier.FREE, request, chatResult -> {
            if (chatResult.text().contains("bad json")) {
                throw new InvalidModelOutputException("JSON could not be parsed");
            }
            return chatResult.text();
        });

        assertEquals("valid output", parsed);
    }
}
