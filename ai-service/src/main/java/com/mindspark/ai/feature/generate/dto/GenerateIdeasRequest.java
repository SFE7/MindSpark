package com.mindspark.ai.feature.generate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GenerateIdeasRequest(
        @NotBlank(message = "Topic is required")
        String topic,

        String context,

        @Min(value = 1, message = "Count must be between 1 and 10")
        @Max(value = 10, message = "Count must be between 1 and 10")
        Integer count
) {
    public int effectiveCount() {
        return (count != null && count > 0) ? count : 5;
    }
}
