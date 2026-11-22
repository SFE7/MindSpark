package com.sme.maintenance.media.dto;

import java.util.List;
import java.util.Map;

public record RoomMediaBundleDto(
        Map<String, List<IdeaAttachmentDto>> ideaAttachmentsByIdeaId,
        List<CanvasImageDto> canvasImages
) {}
