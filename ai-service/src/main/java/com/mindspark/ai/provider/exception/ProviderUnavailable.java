package com.mindspark.ai.provider.exception;

public class ProviderUnavailable extends ProviderException {

    public ProviderUnavailable(String message) {
        super(message, 503);
    }

    public ProviderUnavailable(String message, int statusCode) {
        super(message, statusCode);
    }

    public ProviderUnavailable(String message, Throwable cause) {
        super(message, 503, cause);
    }

    public ProviderUnavailable(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
