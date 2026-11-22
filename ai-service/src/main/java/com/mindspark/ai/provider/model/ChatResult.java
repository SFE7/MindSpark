package com.mindspark.ai.provider.model;

public record ChatResult(
        String text,
        String modelUsed,
        TokenUsage tokenUsage,
        String finishReason
) {
    public ChatResult(String text, String modelUsed, TokenUsage tokenUsage) {
        this(text, modelUsed, tokenUsage, null);
    }
}
