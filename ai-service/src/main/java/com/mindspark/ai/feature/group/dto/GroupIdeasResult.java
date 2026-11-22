package com.mindspark.ai.feature.group.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record GroupIdeasResult(
        List<GroupItem> groups,
        List<String> ungroupedIds
) {
    public record GroupItem(
            String name,
            List<String> ideaIds,
            String rationale
    ) {}

    /**
     * Server-side fix & validation:
     * Ensures every input id appears exactly once across groups + ungroupedIds.
     * Drops unknown IDs and ensures no duplicates.
     */
    public GroupIdeasResult normalizeWithInputIds(List<String> inputIds) {
        Set<String> validIds = new LinkedHashSet<>(inputIds);
        Set<String> seenIds = new HashSet<>();

        List<GroupItem> normalizedGroups = new ArrayList<>();
        if (groups != null) {
            for (GroupItem g : groups) {
                List<String> validGroupIdeaIds = new ArrayList<>();
                if (g.ideaIds() != null) {
                    for (String id : g.ideaIds()) {
                        if (id != null && validIds.contains(id) && seenIds.add(id)) {
                            validGroupIdeaIds.add(id);
                        }
                    }
                }
                normalizedGroups.add(new GroupItem(
                        g.name() != null ? g.name() : "Group",
                        validGroupIdeaIds,
                        g.rationale() != null ? g.rationale() : ""
                ));
            }
        }

        List<String> normalizedUngrouped = new ArrayList<>();
        if (ungroupedIds != null) {
            for (String id : ungroupedIds) {
                if (id != null && validIds.contains(id) && seenIds.add(id)) {
                    normalizedUngrouped.add(id);
                }
            }
        }

        // Add any missing input IDs to ungrouped
        for (String id : validIds) {
            if (!seenIds.contains(id)) {
                normalizedUngrouped.add(id);
                seenIds.add(id);
            }
        }

        return new GroupIdeasResult(normalizedGroups, normalizedUngrouped);
    }
}
