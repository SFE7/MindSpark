package com.mindspark.ai.provider.exception;

public abstract class ProviderException extends RuntimeException {

    private final Integer statusCode;

    public ProviderException(String message) {
        this(message, null, null);
    }

    public ProviderException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ProviderException(String message, Integer statusCode) {
        this(message, statusCode, null);
    }

    public ProviderException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
