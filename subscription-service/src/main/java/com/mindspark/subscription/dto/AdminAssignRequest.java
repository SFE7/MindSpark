package com.mindspark.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminAssignRequest(
        @NotBlank String userId,
        @NotBlank String planCode,
        String reason
) {}
