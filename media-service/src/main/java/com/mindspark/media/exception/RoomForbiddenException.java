package com.mindspark.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RoomForbiddenException extends ResponseStatusException {
    public RoomForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
