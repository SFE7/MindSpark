package com.sme.maintenance.websocket;

import java.util.Map;
import java.util.UUID;

public record CommentChangedEvent(
        UUID roomId,
        String type,
        UUID ideaId,
        UUID commentId,
        Map<String, Object> commentData,
        long version
) {}
