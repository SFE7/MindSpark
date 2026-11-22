package com.sme.maintenance.sessionexport.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdeaExportDto(
        UUID id,
        String title,
        String text,
        Long categoryId,
        String categoryName,
        String authorName,
        String authorKeycloakId,
        int voteCount,
        Instant createdAt,
        List<CommentExportDto> comments
) {}
