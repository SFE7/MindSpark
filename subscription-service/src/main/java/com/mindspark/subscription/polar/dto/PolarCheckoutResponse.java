package com.mindspark.subscription.polar.dto;

public record PolarCheckoutResponse(
        String id,
        String url,
        String status,
        String productId
) {}
