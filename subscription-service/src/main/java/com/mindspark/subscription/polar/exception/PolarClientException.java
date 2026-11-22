package com.mindspark.subscription.polar.exception;

public class PolarClientException extends RuntimeException {
    public PolarClientException(String message) {
        super(message);
    }

    public PolarClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
