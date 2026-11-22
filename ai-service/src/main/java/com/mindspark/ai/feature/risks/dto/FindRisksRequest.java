package com.mindspark.ai.feature.risks.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FindRisksRequest(
        @NotNull(message = "Idea object is required")
        @Valid
        IdeaPayload idea,

        String context
) {
    public record IdeaPayload(
            String id,

            @NotBlank(message = "Idea text is required")
            String text
    ) {}
}
