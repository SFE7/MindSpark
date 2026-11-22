package com.mindspark.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckoutConfirmRequest(
        @NotBlank(message = "checkoutId is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{8,128}$", message = "Invalid checkoutId format")
        String checkoutId
) {}
