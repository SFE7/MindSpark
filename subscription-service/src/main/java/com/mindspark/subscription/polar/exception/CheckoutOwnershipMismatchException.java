package com.mindspark.subscription.polar.exception;

public class CheckoutOwnershipMismatchException extends RuntimeException {
    public CheckoutOwnershipMismatchException(String message) {
        super(message);
    }
}
