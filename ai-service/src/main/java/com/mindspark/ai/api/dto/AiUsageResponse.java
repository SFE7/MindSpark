package com.mindspark.ai.api.dto;

import com.mindspark.ai.tier.Tier;

public record AiUsageResponse(
        Tier tier,
        int dailyLimit,
        int usedToday,
        int remainingToday,
        String resetsAtUtc
) {}
