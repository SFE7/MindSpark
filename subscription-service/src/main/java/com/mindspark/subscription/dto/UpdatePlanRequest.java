package com.mindspark.subscription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePlanRequest(
        @NotBlank String name,
        String description,
        @Min(0) int priceCents,
        @NotBlank String currency,
        @NotBlank String billingInterval,
        @Min(1) int maxActiveRooms,
        @Min(1) int maxParticipantsPerRoom,
        @Min(0) int aiDailyLimit,
        @NotBlank @Pattern(regexp = "(?i)^(FREE|PREMIUM)$", message = "aiTier must be FREE or PREMIUM") String aiTier,
        boolean active,
        int sortOrder
) {}
