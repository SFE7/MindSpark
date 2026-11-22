package com.mindspark.ai.common.exception;

public class BadModelOutputException extends RuntimeException {
    public BadModelOutputException(String message) {
        super(message);
    }

    public BadModelOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
