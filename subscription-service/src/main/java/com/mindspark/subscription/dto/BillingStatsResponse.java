package com.mindspark.subscription.dto;

import java.util.List;

public record BillingStatsResponse(
        long totalSubscriptionsCount,
        long activePaidSubscriptionsCount,
        List<PlanSubscriberCount> subscribersByPlan,
        long totalRevenueCents,
        long estimatedMrrCents,
        long recentBillingEventsCount30d,
        List<BillingRecordResponse> recentBillingRecords
) {
    public record PlanSubscriberCount(
            String planCode,
            String planName,
            long activeCount
    ) {}
}
