package com.mindspark.subscription.polar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mindspark.polar")
public class PolarProperties {

    private String apiBaseUrl = "https://sandbox-api.polar.sh";
    private String accessToken = "";
    private String productIdPremium = "";
    private String productIdTeam = "";

    public boolean isPolarEnabled() {
        return accessToken != null && !accessToken.isBlank()
                && ((productIdPremium != null && !productIdPremium.isBlank())
                || (productIdTeam != null && !productIdTeam.isBlank()));
    }

    public String resolveProductId(String planCode) {
        if (planCode == null) {
            return null;
        }
        return switch (planCode.trim().toUpperCase()) {
            case "PREMIUM" -> (productIdPremium != null && !productIdPremium.isBlank()) ? productIdPremium.trim() : null;
            case "TEAM" -> (productIdTeam != null && !productIdTeam.isBlank()) ? productIdTeam.trim() : null;
            default -> null;
        };
    }

    public String resolvePlanCode(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        String clean = productId.trim();
        if (productIdPremium != null && clean.equals(productIdPremium.trim())) {
            return "PREMIUM";
        }
        if (productIdTeam != null && clean.equals(productIdTeam.trim())) {
            return "TEAM";
        }
        return null;
    }
}
