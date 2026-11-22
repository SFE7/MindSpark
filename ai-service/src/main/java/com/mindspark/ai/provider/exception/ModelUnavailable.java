package com.mindspark.ai.provider.exception;

public class ModelUnavailable extends ProviderException {

    public ModelUnavailable(String message) {
        this(message, 404);
    }

    public ModelUnavailable(String message, int statusCode) {
        super(message, statusCode);
    }
}
