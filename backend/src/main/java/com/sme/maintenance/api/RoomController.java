
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
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.AuthenticatedUserService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final AuthenticatedUserService authenticatedUserService;
    private final RoomService roomService;

    public RoomController(
            RoomService roomService,
            AuthenticatedUserService authenticatedUserService) {
        this.roomService = roomService;
        this.authenticatedUserService = authenticatedUserService;
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

    private RoomResponse response(Room room, Long userId) {

        List<UserResponse> members = roomService.members(room.getId())
                .stream()
                .map(member -> new UserResponse(
                        member.getId(),
                        member.getUsername(),
                        member.getEmail()
                ))
                .toList();

        boolean isMember = members.stream()
                .anyMatch(member -> member.id().equals(userId));

        User owner = room.getOwner();

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getInvitationCode(),
                new UserResponse(
                        owner.getId(),
                        owner.getUsername(),
                        owner.getEmail()
                ),
                room.getCreatedAt(),
                members,
                members.size(),
                isMember
        );
    }
}
