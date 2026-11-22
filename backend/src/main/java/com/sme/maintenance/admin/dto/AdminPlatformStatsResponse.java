package com.sme.maintenance.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPlatformStatsResponse(
        long totalUsers,
        long totalRooms,
        long activeRooms,
        long closedRooms,
        long totalIdeas,
        long totalVotes,
        List<RecentRoomSummary> recentRooms
) {
    public record RecentRoomSummary(
            UUID id,
            String name,
            String invitationCode,
            String ownerUsername,
            Instant createdAt,
            boolean closed,
            int memberCount
    ) {}
}
