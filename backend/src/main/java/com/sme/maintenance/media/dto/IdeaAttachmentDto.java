package com.sme.maintenance.media.dto;

import java.time.Instant;
import java.util.UUID;

public record IdeaAttachmentDto(
        UUID id,
        UUID roomId,
        UUID ideaId,
        String mediaId,
        String url,
        String contentType,
        int width,
        int height,
        Long uploadedById,
        String uploadedBySub,
        String uploadedByName,
        Instant createdAt
) {}
