package com.mindspark.ai.feature.actionplan.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record ActionPlanResult(
        String title,
        List<PlanStep> steps,
        List<String> quickWins
) {
    public record PlanStep(
            int order,
            String title,
            String description,
            String priority,
            List<String> relatedIdeaIds
    ) {}

    public ActionPlanResult normalizeAndFilter(Set<String> validIds) {
        List<PlanStep> normalizedSteps = new ArrayList<>();
        if (steps != null) {
            int currentOrder = 1;
            for (PlanStep step : steps) {
                List<String> validRelated = new ArrayList<>();
                if (step.relatedIdeaIds() != null) {
                    for (String id : step.relatedIdeaIds()) {
                        if (id != null && validIds.contains(id)) {
                            validRelated.add(id);
                        }
                    }
                }
                String prio = "medium";
                if (step.priority() != null) {
                    String p = step.priority().trim().toLowerCase();
                    if (p.equals("high") || p.equals("low") || p.equals("medium")) {
                        prio = p;
                    }
                }
                normalizedSteps.add(new PlanStep(
                        step.order() > 0 ? step.order() : currentOrder,
                        step.title() != null ? step.title() : "Step " + currentOrder,
                        step.description() != null ? step.description() : "",
                        prio,
                        validRelated
                ));
                currentOrder++;
            }
        }
        return new ActionPlanResult(
                title != null ? title : "Action Plan",
                normalizedSteps,
                quickWins != null ? quickWins : List.of()
        );
    }
}
