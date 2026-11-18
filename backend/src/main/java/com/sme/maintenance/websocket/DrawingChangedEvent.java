package com.sme.maintenance.websocket;

import java.util.Map;
import java.util.UUID;

public record DrawingChangedEvent(
        UUID roomId,
        String type,
        UUID drawingId,
        Map<String, Object> drawingData,
        long version
) {}
