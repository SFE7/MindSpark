package com.sme.maintenance.sessionexport.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentExportDto(
        UUID id,
        String authorName,
        String content,
        UUID parentCommentId,
        Instant createdAt
) {}
