package com.mindspark.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record UpgradeRequest(
    @NotBlank(message = "targetPlanCode is required")
    String targetPlanCode,
    String paymentMethodLabel
) {}
