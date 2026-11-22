package com.mindspark.ai.provider.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.tier.Tier;

@Component
public class DefaultLlmClientFactory implements LlmClientFactory {

    private final AiTiersProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, LlmClient> clientCache = new ConcurrentHashMap<>();

    public DefaultLlmClientFactory(
            AiTiersProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmClient getClient(String model, Tier tier) {
        String cacheKey = tier.name() + ":" + model;
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(model, tier));
    }

    private LlmClient createClient(String model, Tier tier) {
        boolean isGemini = (tier == Tier.FREE)
                || properties.getFree().getModels().contains(model)
                || model.toLowerCase().startsWith("gemini");

        String baseUrl = isGemini ? properties.getGeminiBaseUrl() : properties.getGroqBaseUrl();
        String apiKey = isGemini ? properties.getGeminiApiKey() : properties.getGroqApiKey();

        return new OpenAiCompatibleClient(
                restClientBuilder,
                baseUrl,
                apiKey,
                model,
                properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds(),
                properties.getReasoningEffortForModel(model),
                objectMapper
        );
    }
}
