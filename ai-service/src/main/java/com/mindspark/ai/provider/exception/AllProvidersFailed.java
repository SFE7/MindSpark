package com.mindspark.ai.provider.exception;

import lombok.Getter;

@Getter
public class AllProvidersFailed extends RuntimeException {
    private final boolean rateLimited;
    private final Long retryAfterSeconds;

    public AllProvidersFailed(String message, boolean rateLimited, Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.rateLimited = rateLimited;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public AllProvidersFailed(String message, boolean rateLimited, Long retryAfterSeconds) {
        this(message, rateLimited, retryAfterSeconds, null);
    }
}
