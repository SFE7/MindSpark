package com.sme.maintenance.votepool.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record CastVoteRequest(
        @NotNull(message = "Idea ID is required")
        UUID ideaId
) {}
