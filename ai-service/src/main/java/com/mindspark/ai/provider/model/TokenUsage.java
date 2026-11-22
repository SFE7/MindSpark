package com.mindspark.ai.provider.model;

public record TokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer reasoningTokens
) {
    public TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        this(promptTokens, completionTokens, totalTokens, null);
    }
}
