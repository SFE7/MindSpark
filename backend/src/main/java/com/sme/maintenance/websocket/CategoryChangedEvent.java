package com.sme.maintenance.websocket;

import java.util.Map;
import java.util.UUID;

public record CategoryChangedEvent(
        UUID roomId,
        String type,
        Long categoryId,
        Map<String, Object> categoryData,
        long version
) {}
