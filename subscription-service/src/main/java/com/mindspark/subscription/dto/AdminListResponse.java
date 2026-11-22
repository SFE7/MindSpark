package com.mindspark.subscription.dto;

import java.util.List;

public record AdminListResponse(
        boolean configured,
        List<AdminUserSummary> admins
) {
    public record AdminUserSummary(
            String userId,
            String username,
            String email,
            String firstName,
            String lastName,
            String role,
            Long createdTimestamp
    ) {}
}
