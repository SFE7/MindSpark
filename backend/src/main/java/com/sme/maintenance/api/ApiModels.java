package com.sme.maintenance.api;

import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record CreateRoomRequest(String name) {}
    public record JoinRoomRequest(String invitationCode) {}
    public record IdeaRequest(String text) {}
    public record RoomResponse(UUID id, String name, String invitationCode, UUID ownerGuestId,
                               Instant createdAt, int memberCount, boolean member) {}
    public record IdeaResponse(UUID id, UUID roomId, UUID authorGuestId, String text,
                               Instant createdAt, Instant updatedAt, long voteCount, boolean voted) {}
}