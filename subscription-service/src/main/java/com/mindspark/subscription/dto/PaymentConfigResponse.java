package com.mindspark.subscription.dto;

import java.util.List;

public record PaymentConfigResponse(
        String mode,
        String provider,
        List<String> availablePlans
) {}
