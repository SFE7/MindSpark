package com.mindspark.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        Long retryAfterSeconds
) {
    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }
}
