package com.sme.maintenance.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCanvasImageRequest(
        @NotBlank(message = "mediaId is required")
        @Size(max = 64)
        String mediaId,

        @NotBlank(message = "contentType is required")
        @Size(max = 64)
        String contentType,

        @NotNull(message = "x coordinate is required")
        Double x,

        @NotNull(message = "y coordinate is required")
        Double y,

        @NotNull(message = "width is required")
        @Positive
        Double width,

        @NotNull(message = "height is required")
        @Positive
        Double height,

        @NotNull(message = "naturalWidth is required")
        @Positive
        Integer naturalWidth,

        @NotNull(message = "naturalHeight is required")
        @Positive
        Integer naturalHeight,

        Integer zIndex
) {}
