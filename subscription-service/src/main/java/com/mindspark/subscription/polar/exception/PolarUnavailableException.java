package com.mindspark.subscription.polar.exception;

public class PolarUnavailableException extends RuntimeException {
    public PolarUnavailableException(String message) {
        super(message);
    }

    public PolarUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
