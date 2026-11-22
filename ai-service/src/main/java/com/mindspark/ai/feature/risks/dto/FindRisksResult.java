package com.mindspark.ai.feature.risks.dto;

import java.util.ArrayList;
import java.util.List;

public record FindRisksResult(
        List<RiskItem> risks
) {
    public record RiskItem(
            String title,
            String description,
            String severity,
            String mitigation
    ) {}

    public FindRisksResult normalize() {
        List<RiskItem> normalized = new ArrayList<>();
        if (risks != null) {
            for (RiskItem r : risks) {
                String sev = "medium";
                if (r.severity() != null) {
                    String s = r.severity().trim().toLowerCase();
                    if (s.equals("low") || s.equals("medium") || s.equals("high")) {
                        sev = s;
                    }
                }
                normalized.add(new RiskItem(
                        r.title() != null ? r.title() : "Identified Risk",
                        r.description() != null ? r.description() : "",
                        sev,
                        r.mitigation() != null ? r.mitigation() : ""
                ));
            }
        }
        return new FindRisksResult(normalized);
    }
}
