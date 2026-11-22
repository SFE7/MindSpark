package com.mindspark.ai.provider.exception;

import lombok.Getter;

@Getter
public class ProviderRateLimited extends ProviderException {
    private final Long retryAfterSeconds;

    public ProviderRateLimited(String message, Long retryAfterSeconds) {
        super(message, 429);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ProviderRateLimited(String message) {
        this(message, null);
    }
}
