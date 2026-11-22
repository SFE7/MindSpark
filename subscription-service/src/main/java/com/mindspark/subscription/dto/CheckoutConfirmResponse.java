package com.mindspark.subscription.dto;

public record CheckoutConfirmResponse(
        String status,
        boolean alreadyProcessed,
        SubscriptionMeResponse subscription,
        String billingRecordId,
        String checkoutId,
        Long retryAfterMs,
        String message
) {}
