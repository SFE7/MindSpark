package com.mindspark.ai.provider.exception;

public class ProviderBadRequest extends ProviderException {

    public ProviderBadRequest(String message) {
        super(message, 400);
    }

    public ProviderBadRequest(String message, Integer statusCode) {
        super(message, statusCode);
    }

    public ProviderBadRequest(String message, Throwable cause) {
        super(message, 400, cause);
    }
}
