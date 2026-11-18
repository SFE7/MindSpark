package com.sme.maintenance.websocket;

import java.util.Map;
import java.util.UUID;

public record IdeaChangedEvent(
        UUID roomId,
        String type,
        UUID ideaId,
        Map<String, Object> ideaData,
        long version
) {}
