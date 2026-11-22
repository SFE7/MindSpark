package com.mindspark.session.dto;

import java.time.Instant;
import java.util.UUID;

public record SnapshotSummaryResponse(
        UUID id,
        String roomId,
        String roomName,
        String invitationCode,
        String title,
        String notes,
        String savedByUserId,
        String savedByName,
        String ownerUserId,
        int ideaCount,
        int categoryCount,
        int totalVotes,
        int participantCount,
        Instant createdAt,
        boolean hasAiSummary
) {}
