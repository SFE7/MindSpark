package com.mindspark.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribeRequest(
    @NotBlank(message = "planCode is required")
    String planCode,
    String paymentMethodLabel
) {}
