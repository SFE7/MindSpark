package com.mindspark.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RoomNotFoundException extends ResponseStatusException {
    public RoomNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
