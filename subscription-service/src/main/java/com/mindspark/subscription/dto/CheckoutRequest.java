package com.mindspark.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank(message = "Plan code is required")
        String planCode,
        String returnOrigin
) {}
