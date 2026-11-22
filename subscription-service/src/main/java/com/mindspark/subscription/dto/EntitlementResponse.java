package com.mindspark.subscription.dto;

public record EntitlementResponse(
    String userId,
    String planCode,
    String planName,
    String aiTier,
    int maxActiveRooms,
    int maxParticipantsPerRoom,
    int aiDailyLimit
) {}
