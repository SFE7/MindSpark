package com.mindspark.ai.provider.exception;

public class ProviderAuthError extends ProviderException {

    public ProviderAuthError(String message) {
        this(message, 401);
    }

    public ProviderAuthError(String message, int statusCode) {
        super(message, statusCode);
    }
}
