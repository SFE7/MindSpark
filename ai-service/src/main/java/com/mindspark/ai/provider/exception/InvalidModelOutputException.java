package com.mindspark.ai.provider.exception;

public class InvalidModelOutputException extends ProviderException {
    public InvalidModelOutputException(String message) {
        super(message);
    }

    public InvalidModelOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
