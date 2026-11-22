package com.mindspark.subscription.polar.exception;

import lombok.Getter;

@Getter
public class PaymentNotCompletedException extends RuntimeException {
    private final String polarStatus;

    public PaymentNotCompletedException(String message, String polarStatus) {
        super(message);
        this.polarStatus = polarStatus;
    }
}
