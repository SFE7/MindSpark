package com.mindspark.session.dto;

import java.time.Instant;
import java.util.UUID;

public record BackendCommentDto(
        UUID id,
        String authorName,
        String content,
        UUID parentCommentId,
        Instant createdAt
) {}
