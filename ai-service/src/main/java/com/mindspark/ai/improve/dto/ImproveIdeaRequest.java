package com.mindspark.ai.feature.improve.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ImproveIdeaRequest(
        @NotNull(message = "Idea object is required")
        @Valid
        IdeaPayload idea,

        @NotBlank(message = "Mode is required")
        @Pattern(regexp = "^(?i)(improve|expand)$", message = "Mode must be 'improve' or 'expand'")
        String mode
) {
    public record IdeaPayload(
            String id,
            @NotBlank(message = "Idea text is required")
            String text
    ) {}
}
