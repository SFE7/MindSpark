package com.sme.maintenance.subscription;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class SubscriptionExceptionHandler {

    @ExceptionHandler(RoomLimitReachedException.class)
    public ResponseEntity<Map<String, Object>> handleRoomLimitReached(RoomLimitReachedException ex) {
        log.warn("Room limit reached: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "ROOM_LIMIT_REACHED");
        body.put("message", ex.getMessage());
        body.put("limit", ex.getLimit());
        body.put("current", ex.getCurrent());
        body.put("upgradeRequired", true);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ParticipantLimitReachedException.class)
    public ResponseEntity<Map<String, Object>> handleParticipantLimitReached(ParticipantLimitReachedException ex) {
        log.warn("Participant limit reached: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "PARTICIPANT_LIMIT_REACHED");
        body.put("message", ex.getMessage());
        body.put("limit", ex.getLimit());
        body.put("current", ex.getCurrent());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(SubscriptionServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleSubscriptionServiceUnavailable(SubscriptionServiceUnavailableException ex) {
        log.error("Subscription service unavailable: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "SUBSCRIPTION_SERVICE_UNAVAILABLE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
