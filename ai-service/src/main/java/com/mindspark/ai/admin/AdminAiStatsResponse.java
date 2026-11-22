package com.mindspark.ai.admin;

import java.util.Map;

public record AdminAiStatsResponse(
        int callsTodayUtc,
        int totalCallsAllTime,
        int activeAiUsersToday,
        Map<String, Integer> tierBreakdownToday
) {}
