
package com.sme.maintenance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.CreateRoomRequest;
import com.sme.maintenance.api.ApiModels.JoinRoomRequest;
import com.sme.maintenance.api.ApiModels.RoomResponse;
import com.sme.maintenance.api.ApiModels.UpdateRoomRequest;
import com.sme.maintenance.api.ApiModels.UserResponse;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.AuthenticatedUserService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final AuthenticatedUserService authenticatedUserService;
    private final RoomService roomService;
    private final PresenceService presenceService;

    public RoomController(
            RoomService roomService,
            AuthenticatedUserService authenticatedUserService,
            PresenceService presenceService) {
        this.roomService = roomService;
        this.authenticatedUserService = authenticatedUserService;
        this.presenceService = presenceService;
    }

    @PostMapping
    public RoomResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateRoomRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.create(request.name(), user.getId());

        return response(room, user.getId());
    }

    @PutMapping("/{roomId}")
    public RoomResponse update(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateRoomRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.update(roomId, request.name(), user.getId());

        return response(room, user.getId());
    }

    @GetMapping
    public List<RoomResponse> owned(
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        return roomService.ownedBy(user.getId())
                .stream()
                .map(room -> response(room, user.getId()))
                .toList();
    }

    @PostMapping("/join")
    public RoomResponse join(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JoinRoomRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.join(
                request.invitationCode(),
                user.getId()
        );

        return response(room, user.getId());
    }

    @GetMapping("/{roomId}")
    public RoomResponse get(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.memberRoom(roomId, user.getId());

        return response(room, user.getId());
    }

    @PostMapping("/{roomId}/reopen")
    public RoomResponse reopen(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.reopen(roomId, user.getId());

        return response(room, user.getId());
    }

    @DeleteMapping("/{roomId}/leave")
    public RoomResponse leave(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.leave(roomId, user.getId());

        return response(room, user.getId());
    }
//As a room owner, I can remove a member from the room.
    @DeleteMapping("/{roomId}/members/{memberId}")
    public RoomResponse removeMember(
            @PathVariable UUID roomId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.removeMember(roomId, memberId, user.getId());

        return response(room, user.getId());
    }
//As a room owner, I can close the room to prevent new members from joining
    @PostMapping("/{roomId}/close")
    public RoomResponse close(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.close(roomId, user.getId());

        return response(room, user.getId());
    }

    @PostMapping("/{roomId}/open")
    public RoomResponse open(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        Room room = roomService.open(roomId, user.getId());

        return response(room, user.getId());
    }
//As a room owner, I can delete the room and all its ideas and votes
    @DeleteMapping("/{roomId}")
    public void delete(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);

        roomService.delete(roomId, user.getId());
    }
//As a room owner, I can remove a member from the room
    private RoomResponse response(Room room, Long userId) {

        List<UserResponse> members = roomService.members(room.getId())
                .stream()
                .map(member -> new UserResponse(
                        member.getId(),
                        member.getUsername(),
                        null
                ))
                .toList();

        boolean isMember = members.stream()
                .anyMatch(member -> member.id().equals(userId));

        User owner = room.getOwner();
        boolean offline = presenceService.isRoomOffline(room.getId(), room.getOwnerId());

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getInvitationCode(),
                new UserResponse(
                        owner.getId(),
                        owner.getUsername(),
                        null
                ),
                room.getCreatedAt(),
                members,
                members.size(),
                isMember,
                room.isClosed(),
                offline
        );
    }
}
