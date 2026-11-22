package com.sme.maintenance.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateIdeaAttachmentRequest(
        @NotBlank(message = "mediaId is required")
        @Size(max = 64)
        String mediaId,

        @NotBlank(message = "contentType is required")
        @Size(max = 64)
        String contentType,

        @NotNull(message = "width is required")
        @Positive
        Integer width,

        @NotNull(message = "height is required")
        @Positive
        Integer height
) {}
