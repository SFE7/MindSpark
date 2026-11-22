package com.sme.maintenance.votepool.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sme.maintenance.votepool.VotePoolStatus;

public record VotePoolResponse(
        UUID id,
        UUID roomId,
        String title,
        int votesPerParticipant,
        boolean allowMultipleVotesPerIdea,
        VotePoolStatus status,
        Long createdById,
        String createdBySub,
        String createdByName,
        Instant createdAt,
        Instant closedAt,
        int myVotesUsed,
        int myVotesRemaining,
        Map<String, Integer> myVotesByIdeaId,
        List<VotePoolTallyDto> tallies
) {}
