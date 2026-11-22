package com.sme.maintenance.sessionexport.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomExportDataResponse(
        UUID roomId,
        String roomName,
        String invitationCode,
        boolean closed,
        Instant createdAt,
        String ownerKeycloakId,
        String ownerName,
        List<ParticipantExportDto> participants,
        List<CategoryExportDto> categories,
        List<IdeaExportDto> ideas
) {}
