package com.mindspark.ai.feature.summarize.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SummarizeSessionRequest(
        String sessionTitle,

        @NotNull(message = "Ideas list is required")
        @Size(min = 1, max = 30, message = "Summarize requires between 1 and 30 ideas")
        @Valid
        List<SessionIdeaItem> ideas
) {
    public record SessionIdeaItem(
            @NotBlank(message = "Idea id is required")
            String id,

            @NotBlank(message = "Idea text is required")
            String text,

            Integer votes,

            String category
    ) {}
}
