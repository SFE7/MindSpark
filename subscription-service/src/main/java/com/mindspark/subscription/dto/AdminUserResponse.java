package com.mindspark.subscription.dto;

public record AdminUserResponse(
        String userId,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean temporaryPasswordRequired
) {}
