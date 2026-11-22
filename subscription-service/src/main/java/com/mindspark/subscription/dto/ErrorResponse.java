package com.mindspark.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        String polarStatus
) {
    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }
}
