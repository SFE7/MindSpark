package com.sme.maintenance.admin.dto;

import java.util.Date;

public record AdminUserDirectoryResponse(
        Long id,
        String keycloakId,
        String email,
        String fullName,
        Date createdAt
) {}
