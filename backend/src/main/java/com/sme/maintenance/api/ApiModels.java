
package com.sme.maintenance.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sme.maintenance.room.Room;

public final class ApiModels {

    private ApiModels() {}

    //Requests records
    public record CreateRoomRequest(String name) {}

    public record UpdateRoomRequest(String name) {}

    public record JoinRoomRequest(String invitationCode) {}

    public record PresenceRequest(String clientId) {}

    public record IdeaRequest(
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Long categoryId
    ) {}

    public record  CategoryRequest(String title){}

    public record CommentRequest(
            String text,
            String content,
            UUID parentId
    ) {
        public String effectiveContent() {
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
            return "";
        }
    }


    // Responses records

    public record UserResponse(
            Long id,
            String username,
            String email
    ) {}

    public record RoomResponse(
            UUID id,
            String name,
            String invitationCode,
            UserResponse owner,
            Instant createdAt,
            List<UserResponse> members,
            int memberCount,
            boolean member,
            boolean closed,
            boolean offline
    ) {
        public RoomResponse(UUID id, String name, String invitationCode, UserResponse owner,
                            Instant createdAt, List<UserResponse> members, int memberCount,
                            boolean member, boolean closed) {
            this(id, name, invitationCode, owner, createdAt, members, memberCount, member, closed, false);
        }
    }


    public record IdeaResponse(
            UUID id,
            RoomResponse room,
            UserResponse author,
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Instant createdAt,
            Instant updatedAt,
            long voteCount,
            boolean voted,
            CategoryResponse category
    ) {}

    public record CategoryResponse(
        Long id,
        String title
    ) {
    }

    public record CommentResponse(
            UUID id,
            UUID ideaId,
            UserResponse author,
            String content,
            String text,
            UUID parentId,
            Instant createdAt,
            Instant updatedAt,
            List<CommentResponse> replies
    ) {}

    // filters

    public record IdeaFilters(
        Long categoryId,
        String search,
        String sortBy
    ) {
    }
}
