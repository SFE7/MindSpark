package com.mindspark.ai.feature.summarize.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record SummarizeSessionResult(
        String summary,
        List<String> themes,
        List<TopIdeaItem> topIdeas,
        List<String> openQuestions
) {
    public record TopIdeaItem(
            String id,
            String reason
    ) {}

    public SummarizeSessionResult filterUnknownIds(Set<String> validIds) {
        List<TopIdeaItem> filteredTopIdeas = new ArrayList<>();
        if (topIdeas != null) {
            for (TopIdeaItem item : topIdeas) {
                if (item.id() != null && validIds.contains(item.id())) {
                    filteredTopIdeas.add(item);
                }
            }
        }
        return new SummarizeSessionResult(
                summary != null ? summary : "",
                themes != null ? themes : List.of(),
                filteredTopIdeas,
                openQuestions != null ? openQuestions : List.of()
        );
    }
}
