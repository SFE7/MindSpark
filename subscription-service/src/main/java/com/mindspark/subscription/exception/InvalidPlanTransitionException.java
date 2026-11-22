package com.mindspark.subscription.exception;

public class InvalidPlanTransitionException extends RuntimeException {
    public InvalidPlanTransitionException(String message) {
        super(message);
    }
}
