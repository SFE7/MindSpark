package com.sme.maintenance.subscription;

public record EntitlementDto(
    String userId,
    String planCode,
    String planName,
    String aiTier,
    int maxActiveRooms,
    int maxParticipantsPerRoom,
    int aiDailyLimit
) {}
