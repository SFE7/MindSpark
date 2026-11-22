package com.mindspark.ai.feature.improve.dto;

import java.util.List;

public record ImproveIdeaResult(
        String title,
        String text,
        List<String> changes
) {}
