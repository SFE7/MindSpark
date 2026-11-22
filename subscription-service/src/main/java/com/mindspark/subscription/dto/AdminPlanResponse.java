package com.mindspark.subscription.dto;

import java.util.UUID;

public record AdminPlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        int priceCents,
        String currency,
        String billingInterval,
        int maxActiveRooms,
        int maxParticipantsPerRoom,
        int aiDailyLimit,
        String aiTier,
        boolean active,
        int sortOrder,
        long activeSubscribersCount
) {}
