package com.mindspark.session.dto;

import java.util.List;
import java.util.UUID;

public record BackendParticipantDto(
        String keycloakId,
        String displayName,
        String role
) {}
