package com.mindspark.ai.provider.model;

import lombok.Builder;

@Builder
public record ChatRequest(
        String systemPrompt,
        String userPrompt,
        Integer maxOutputTokens,
        Double temperature,
        boolean wantJson
) {
    public ChatRequest {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt cannot be null or blank");
        }
        if (temperature == null) {
            temperature = 0.7;
        }
    }
}
