package com.mindspark.ai.feature.group.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupIdeasRequest(
        @NotNull(message = "Ideas list is required")
        @Size(min = 2, max = 30, message = "Group ideas requires between 2 and 30 ideas")
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
