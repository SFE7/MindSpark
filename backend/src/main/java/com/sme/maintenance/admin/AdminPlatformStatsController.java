package com.sme.maintenance.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.admin.dto.AdminPlatformStatsResponse;
import com.sme.maintenance.admin.dto.AdminPlatformStatsResponse.RecentRoomSummary;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.vote.VoteRepository;

@RestController
@RequestMapping("/api/admin/stats/platform")
public class AdminPlatformStatsController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;

    public AdminPlatformStatsController(
            UserRepository userRepository,
            RoomRepository roomRepository,
            IdeaRepository ideaRepository,
            VoteRepository voteRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.ideaRepository = ideaRepository;
        this.voteRepository = voteRepository;
    }

    @GetMapping
    public ResponseEntity<AdminPlatformStatsResponse> getPlatformStats() {
        long totalUsers = userRepository.count();
        long totalRooms = roomRepository.count();
        long activeRooms = roomRepository.countByClosedFalse();
        long closedRooms = roomRepository.countByClosedTrue();
        long totalIdeas = ideaRepository.count();
        long totalVotes = voteRepository.count();

        List<RecentRoomSummary> recentRooms = roomRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(this::toRecentRoomSummary)
                .toList();

        return ResponseEntity.ok(new AdminPlatformStatsResponse(
                totalUsers,
                totalRooms,
                activeRooms,
                closedRooms,
                totalIdeas,
                totalVotes,
                recentRooms
        ));
    }

    private RecentRoomSummary toRecentRoomSummary(Room room) {
        String ownerUsername = (room.getOwner() != null && room.getOwner().getUsername() != null)
                ? room.getOwner().getUsername()
                : "Unknown";
        int memberCount = room.getMemberIds() != null ? room.getMemberIds().size() : 0;

        return new RecentRoomSummary(
                room.getId(),
                room.getName(),
                room.getInvitationCode(),
                ownerUsername,
                room.getCreatedAt(),
                room.isClosed(),
                memberCount
        );
    }
}
