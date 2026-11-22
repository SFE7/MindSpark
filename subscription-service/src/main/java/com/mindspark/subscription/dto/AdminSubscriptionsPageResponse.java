package com.mindspark.subscription.dto;

import java.util.List;

public record AdminSubscriptionsPageResponse(
        List<AdminSubscriptionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
