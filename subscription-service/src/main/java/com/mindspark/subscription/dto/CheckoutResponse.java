package com.mindspark.subscription.dto;

public record CheckoutResponse(
        String mode,
        String provider,
        String checkoutId,
        String checkoutUrl,
        String planCode,
        String message
) {}
