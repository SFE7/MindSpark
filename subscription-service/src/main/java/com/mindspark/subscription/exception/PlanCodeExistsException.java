package com.mindspark.subscription.exception;

public class PlanCodeExistsException extends RuntimeException {
    public PlanCodeExistsException(String message) {
        super(message);
    }
}
