package com.mindspark.subscription.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubscriptionResponse(
        UUID id,
        String userId,
        String planCode,
        String planName,
        String status,
        Instant startedAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant canceledAt,
        Instant createdAt
) {}
