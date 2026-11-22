package com.sme.maintenance.media.dto;

import java.time.Instant;
import java.util.UUID;

public record CanvasImageDto(
        UUID id,
        UUID roomId,
        String mediaId,
        String url,
        String contentType,
        double x,
        double y,
        double width,
        double height,
        int naturalWidth,
        int naturalHeight,
        int zIndex,
        Long uploadedById,
        String uploadedBySub,
        String uploadedByName,
        Instant createdAt
) {}
