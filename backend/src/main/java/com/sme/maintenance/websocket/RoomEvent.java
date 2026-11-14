package com.sme.maintenance.websocket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomEvent(
        UUID roomId,
        String type,
        long sequence,
        Instant timestamp,
        SharedRoom room,
        SharedIdea idea,
        SharedCategory category,
        UUID ideaId,
        Long categoryId,
        Boolean offline
) {
    public record SharedUser(Long id, String username, String email) {}

    public record SharedRoom(
            UUID id,
            String name,
            String invitationCode,
            SharedUser owner,
            List<SharedUser> members,
            int memberCount,
            boolean closed,
            boolean offline
    ) {}

    public record SharedIdea(
            UUID id,
            String title,
            String description,
            String text,
            Double x,
            Double y,
            String color,
            Long categoryId,
            String categoryTitle,
            long voteCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SharedCategory(
            Long id,
            String title
    ) {}

    public static RoomEvent roomUpdated(UUID roomId, long sequence, Instant now, SharedRoom room) {
        return new RoomEvent(roomId, "room.updated", sequence, now, room, null, null, null, null, null);
    }

    public static RoomEvent roomOffline(UUID roomId, long sequence, Instant now, boolean offline) {
        return new RoomEvent(roomId, "room.offline", sequence, now, null, null, null, null, null, offline);
    }

    public static RoomEvent ideaCreated(UUID roomId, long sequence, Instant now, SharedIdea idea) {
        return new RoomEvent(roomId, "idea.created", sequence, now, null, idea, null, null, null, null);
    }

    public static RoomEvent ideaUpdated(UUID roomId, long sequence, Instant now, SharedIdea idea) {
        return new RoomEvent(roomId, "idea.updated", sequence, now, null, idea, null, null, null, null);
    }

    public static RoomEvent ideaMoved(UUID roomId, long sequence, Instant now, SharedIdea idea) {
        return new RoomEvent(roomId, "idea.moved", sequence, now, null, idea, null, null, null, null);
    }

    public static RoomEvent ideaVoted(UUID roomId, long sequence, Instant now, SharedIdea idea) {
        return new RoomEvent(roomId, "idea.voted", sequence, now, null, idea, null, null, null, null);
    }

    public static RoomEvent ideaDeleted(UUID roomId, long sequence, Instant now, UUID ideaId) {
        return new RoomEvent(roomId, "idea.deleted", sequence, now, null, null, null, ideaId, null, null);
    }

    public static RoomEvent categoryCreated(UUID roomId, long sequence, Instant now, SharedCategory category) {
        return new RoomEvent(roomId, "category.created", sequence, now, null, null, category, null, null, null);
    }

    public static RoomEvent categoryUpdated(UUID roomId, long sequence, Instant now, SharedCategory category) {
        return new RoomEvent(roomId, "category.updated", sequence, now, null, null, category, null, null, null);
    }

    public static RoomEvent categoryDeleted(UUID roomId, long sequence, Instant now, Long categoryId) {
        return new RoomEvent(roomId, "category.deleted", sequence, now, null, null, null, null, categoryId, null);
    }
}
