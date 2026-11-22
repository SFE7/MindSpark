package com.sme.maintenance.sessionexport.dto;

import java.util.UUID;

public record ParticipantExportDto(
        String keycloakId,
        String displayName,
        String role
) {}
