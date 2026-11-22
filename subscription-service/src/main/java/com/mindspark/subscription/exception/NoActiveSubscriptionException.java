package com.mindspark.subscription.exception;

public class NoActiveSubscriptionException extends RuntimeException {
    public NoActiveSubscriptionException(String message) {
        super(message);
    }
}
