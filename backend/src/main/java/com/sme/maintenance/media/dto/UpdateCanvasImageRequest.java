package com.sme.maintenance.media.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCanvasImageRequest(
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

        Integer zIndex
) {}
