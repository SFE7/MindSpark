package com.sme.maintenance.api;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.PresenceRequest;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.presence.PresenceService;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;
import com.sme.maintenance.services.AuthenticatedUserService;

@RestController
@RequestMapping("/api/rooms/{roomId}/presence")
public class PresenceController {

    private final PresenceService presenceService;
    private final RoomService roomService;
    private final AuthenticatedUserService authenticatedUserService;

    public PresenceController(PresenceService presenceService,
                              RoomService roomService,
                              AuthenticatedUserService authenticatedUserService) {
        this.presenceService = presenceService;
        this.roomService = roomService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping
    public void heartbeat(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PresenceRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Room room = roomService.memberRoom(roomId, user.getId());

        boolean isOwner = room.getOwnerId().equals(user.getId());
        presenceService.heartbeat(roomId, user.getId(), request.clientId(), isOwner);
    }

    @PostMapping("/leave")
    public void leave(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PresenceRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Room room = roomService.memberRoom(roomId, user.getId());

        boolean isOwner = room.getOwnerId().equals(user.getId());
        presenceService.leave(roomId, user.getId(), request.clientId(), isOwner);
    }
}
