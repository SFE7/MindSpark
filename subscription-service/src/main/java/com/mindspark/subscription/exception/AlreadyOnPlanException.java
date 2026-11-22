package com.mindspark.subscription.exception;

public class AlreadyOnPlanException extends RuntimeException {
    public AlreadyOnPlanException(String message) {
        super(message);
    }
}
