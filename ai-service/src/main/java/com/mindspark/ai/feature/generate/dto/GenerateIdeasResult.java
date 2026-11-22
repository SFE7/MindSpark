package com.mindspark.ai.feature.generate.dto;

import java.util.List;

public record GenerateIdeasResult(
        List<IdeaItem> ideas
) {
    public record IdeaItem(
            String title,
            String description
    ) {}
}
