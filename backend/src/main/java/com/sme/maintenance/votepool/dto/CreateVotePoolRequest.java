package com.sme.maintenance.votepool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVotePoolRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 120, message = "Title must not exceed 120 characters")
        String title,

        @Min(value = 1, message = "Votes per participant must be at least 1")
        @Max(value = 20, message = "Votes per participant must not exceed 20")
        Integer votesPerParticipant,

        Boolean allowMultipleVotesPerIdea
) {}
