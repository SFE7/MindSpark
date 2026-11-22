package com.mindspark.media.dto;

public record MediaUploadResponse(
        String mediaId,
        String roomId,
        String url,
        String contentType,
        int width,
        int height,
        long sizeBytes
) {}
