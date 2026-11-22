package com.mindspark.subscription.exception;

public class DowngradeNotSupportedException extends RuntimeException {
    public DowngradeNotSupportedException(String message) {
        super(message);
    }
}
