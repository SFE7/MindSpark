package com.sme.maintenance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomService;

import static com.sme.maintenance.api.ApiModels.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public RoomResponse create(@RequestHeader("X-Guest-Id") UUID guestId,
                               @RequestBody CreateRoomRequest request) {
        return response(roomService.create(request.name(), guestId), guestId);
    }

    @GetMapping
    public List<RoomResponse> owned(@RequestHeader("X-Guest-Id") UUID guestId) {
        return roomService.ownedBy(guestId).stream().map(room -> response(room, guestId)).toList();
    }

    @PostMapping("/join")
    public RoomResponse join(@RequestHeader("X-Guest-Id") UUID guestId,
                             @RequestBody JoinRoomRequest request) {
        return response(roomService.join(request.invitationCode(), guestId), guestId);
    }

    @GetMapping("/{roomId}")
    public RoomResponse get(@PathVariable UUID roomId, @RequestHeader("X-Guest-Id") UUID guestId) {
        return response(roomService.memberRoom(roomId, guestId), guestId);
    }

    @PostMapping("/{roomId}/reopen")
    public RoomResponse reopen(@PathVariable UUID roomId, @RequestHeader("X-Guest-Id") UUID guestId) {
        return response(roomService.reopen(roomId, guestId), guestId);
    }

    @DeleteMapping("/{roomId}/leave")
    public RoomResponse leave(@PathVariable UUID roomId, @RequestHeader("X-Guest-Id") UUID guestId) {
        return response(roomService.leave(roomId, guestId), guestId);
    }

    private RoomResponse response(Room room, UUID guestId) {
        return new RoomResponse(room.getId(), room.getName(), room.getInvitationCode(), room.getOwnerGuestId(),
                room.getCreatedAt(), room.getMemberGuestIds().size(), room.getMemberGuestIds().contains(guestId));
    }
}