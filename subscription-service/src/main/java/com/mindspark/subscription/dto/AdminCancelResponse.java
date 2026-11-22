package com.mindspark.subscription.dto;

public record AdminCancelResponse(
        AdminSubscriptionResponse subscription,
        String roleSyncStatus
) {}
