package com.mindspark.session.exception;

public class SnapshotNotFoundException extends RuntimeException {
    public SnapshotNotFoundException(String message) {
        super(message);
    }
}
