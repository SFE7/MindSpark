package com.mindspark.media.dto;

import java.time.Instant;

public record MediaMetadata(
        String mediaId,
        String roomId,
        String uploaderSub,
        String contentType,
        String extension,
        int width,
        int height,
        long sizeBytes,
        Instant createdAt
) {}
