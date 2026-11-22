package com.sme.maintenance.subscription;

public class SubscriptionServiceUnavailableException extends RuntimeException {

    public SubscriptionServiceUnavailableException(String message) {
        super(message);
    }

    public SubscriptionServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
