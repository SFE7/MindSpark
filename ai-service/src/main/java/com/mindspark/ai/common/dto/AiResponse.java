package com.mindspark.ai.common.dto;

public record AiResponse<T>(
        T result,
        Meta meta
) {
    public record Meta(
            String model,
            String tier
    ) {}
}
