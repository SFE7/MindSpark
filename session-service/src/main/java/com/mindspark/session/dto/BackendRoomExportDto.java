package com.mindspark.session.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BackendRoomExportDto(
        UUID roomId,
        String roomName,
        String invitationCode,
        boolean closed,
        Instant createdAt,
        String ownerKeycloakId,
        String ownerName,
        List<BackendParticipantDto> participants,
        List<BackendCategoryDto> categories,
        List<BackendIdeaDto> ideas
) {}
