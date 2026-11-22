package com.mindspark.ai.config;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mindspark.ai.tier.Tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiTiersPropertiesTest {

    @Test
    @DisplayName("Verify default model list and daily limit for FREE tier")
    void testFreeTierDefaults() {
        AiTiersProperties properties = new AiTiersProperties();
        AiTiersProperties.TierConfig freeConfig = properties.getTierConfig(Tier.FREE);

        assertNotNull(freeConfig);
        assertEquals(10, freeConfig.getDailyRequestLimit());
        assertEquals(List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"), freeConfig.getModels());
    }

    @Test
    @DisplayName("Verify default model list and daily limit for PREMIUM tier")
    void testPremiumTierDefaults() {
        AiTiersProperties properties = new AiTiersProperties();
        AiTiersProperties.TierConfig premiumConfig = properties.getTierConfig(Tier.PREMIUM);

        assertNotNull(premiumConfig);
        assertEquals(20, premiumConfig.getDailyRequestLimit());
        assertEquals(List.of("openai/gpt-oss-120b", "qwen/qwen3.8-27b"), premiumConfig.getModels());
    }

    @Test
    @DisplayName("Verify API keys default to empty strings and are excluded from toString")
    void testApiKeysDefaultAndNeverLogged() {
        AiTiersProperties properties = new AiTiersProperties();
        assertEquals("", properties.getGeminiApiKey());
        assertEquals("", properties.getGroqApiKey());

        properties.setGeminiApiKey("secret-gemini-key");
        properties.setGroqApiKey("secret-groq-key");

        String stringRepresentation = properties.toString();
        assertFalse(stringRepresentation.contains("secret-gemini-key"), "Gemini API key must not appear in toString");
        assertFalse(stringRepresentation.contains("secret-groq-key"), "Groq API key must not appear in toString");
    }
}
