package com.mindspark.subscription.dto;

import java.time.Instant;

public record SubscriptionMeResponse(
    String planCode,
    String planName,
    String status,
    Instant startedAt,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant canceledAt,
    int maxActiveRooms,
    int maxParticipantsPerRoom,
    int aiDailyLimit,
    String aiTier
) {}
