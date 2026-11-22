package com.mindspark.ai.feature.actionplan.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActionPlanRequest(
        String goal,

        @NotNull(message = "Ideas list is required")
        @Size(min = 1, max = 15, message = "Action plan requires between 1 and 15 ideas")
        @Valid
        List<IdeaItem> ideas
) {
    public record IdeaItem(
            @NotBlank(message = "Idea id is required")
            String id,

            @NotBlank(message = "Idea text is required")
            String text
    ) {}
}
