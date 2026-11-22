package com.sme.maintenance.votepool.dto;

import java.util.UUID;

public record VotePoolTallyDto(
        UUID ideaId,
        String ideaTitle,
        long poolVotes
) {}
