package com.mindspark.ai.feature.compare.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CompareIdeasResult(
        List<String> criteria,
        List<ComparisonItem> comparison,
        Recommendation recommendation
) {
    public record ComparisonItem(
            String ideaId,
            Map<String, Integer> scores,
            List<String> strengths,
            List<String> weaknesses
    ) {}

    public record Recommendation(
            String ideaId,
            String reason
    ) {}

    public CompareIdeasResult filterAndValidate(Set<String> validIds) {
        List<ComparisonItem> filtered = new ArrayList<>();
        if (comparison != null) {
            for (ComparisonItem c : comparison) {
                if (c.ideaId() != null && validIds.contains(c.ideaId())) {
                    filtered.add(c);
                }
            }
        }

        Recommendation validRec = recommendation;
        if (validRec == null || validRec.ideaId() == null || !validIds.contains(validRec.ideaId())) {
            String fallbackId = validIds.iterator().next();
            validRec = new Recommendation(fallbackId, validRec != null && validRec.reason() != null ? validRec.reason() : "Recommended choice");
        }

        return new CompareIdeasResult(
                criteria != null ? criteria : List.of(),
                filtered,
                validRec
        );
    }
}
