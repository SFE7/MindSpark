package com.mindspark.subscription.exception;

public class KeycloakRoleNotFoundException extends RuntimeException {
    public KeycloakRoleNotFoundException(String message) {
        super(message);
    }
}
