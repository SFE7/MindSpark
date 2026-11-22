package com.mindspark.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSnapshotRequest(
        @NotBlank(message = "roomId is required")
        String roomId,

        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,

        @Size(max = 2000, message = "notes must not exceed 2000 characters")
        String notes,

        @Size(max = 15000, message = "aiSummary must not exceed 15000 characters")
        String aiSummary
) {}
