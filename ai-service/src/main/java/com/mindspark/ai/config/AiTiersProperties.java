package com.mindspark.ai.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.mindspark.ai.tier.Tier;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "ai.tiers")
public class AiTiersProperties {

    private TierConfig free = new TierConfig(
            List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"),
            10
    );

    private TierConfig premium = new TierConfig(
            List.of("openai/gpt-oss-120b", "qwen/qwen3.8-27b"),
            20
    );

    @ToString.Exclude
    private String geminiApiKey = "";

    @ToString.Exclude
    private String groqApiKey = "";

    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";

    private String groqBaseUrl = "https://api.groq.com/openai/v1";

    private int connectTimeoutSeconds = 5;

    private int readTimeoutSeconds = 45;

    private int totalTimeoutSeconds = 50;

    private int perAttemptTimeoutSeconds = 25;

    private String defaultReasoningEffort = "low";

    private java.util.Map<String, String> reasoningEfforts = new java.util.HashMap<>();

    public String getReasoningEffortForModel(String model) {
        if (model != null && reasoningEfforts.containsKey(model)) {
            return reasoningEfforts.get(model);
        }
        return defaultReasoningEffort;
    }

    public TierConfig getTierConfig(Tier tier) {
        return tier == Tier.PREMIUM ? premium : free;
    }

    @Getter
    @Setter
    @ToString
    public static class TierConfig {
        private List<String> models = new ArrayList<>();
        private int dailyRequestLimit;

        public TierConfig() {}

        public TierConfig(List<String> models, int dailyRequestLimit) {
            this.models = new ArrayList<>(models);
            this.dailyRequestLimit = dailyRequestLimit;
        }

        public List<String> getModels() {
            return Collections.unmodifiableList(models);
        }

        public void setModels(List<String> models) {
            this.models = (models != null) ? new ArrayList<>(models) : new ArrayList<>();
        }
    }
}
