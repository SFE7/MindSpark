package com.mindspark.subscription.polar.dto;

import java.util.Map;

public record PolarCheckoutDetails(
        String id,
        String status,
        String productId,
        int amount,
        String currency,
        String externalCustomerId,
        String customerEmail,
        Map<String, Object> metadata
) {}
