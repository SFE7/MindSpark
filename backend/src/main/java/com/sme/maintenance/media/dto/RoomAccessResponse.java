package com.sme.maintenance.media.dto;

import java.util.UUID;

public record RoomAccessResponse(
        UUID roomId,
        boolean isMember,
        boolean isOwner,
        boolean roomClosed
) {}
