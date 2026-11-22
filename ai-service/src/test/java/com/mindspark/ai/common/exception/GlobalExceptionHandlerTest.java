package com.mindspark.ai.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.mindspark.ai.common.dto.ErrorResponse;
import com.mindspark.ai.provider.exception.AllProvidersFailed;
import com.mindspark.ai.provider.exception.InvalidModelOutputException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ValidationException maps to 400 VALIDATION_ERROR")
    void testValidationException() {
        ValidationException ex = new ValidationException("Topic is required");
        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().error());
        assertEquals("Topic is required", response.getBody().message());
        assertNull(response.getBody().retryAfterSeconds());
    }

    @Test
    @DisplayName("DailyLimitReachedException maps to 429 DAILY_LIMIT_REACHED with helpful message")
    void testDailyLimitReached() {
        DailyLimitReachedException ex = new DailyLimitReachedException(10);
        ResponseEntity<ErrorResponse> response = handler.handleDailyLimitReached(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DAILY_LIMIT_REACHED", response.getBody().error());
        assertEquals("Daily request limit reached (10 requests/day). Premium users get a higher daily limit.",
                response.getBody().message());
    }

    @Test
    @DisplayName("AllProvidersFailed with rate-limiting maps to 429 PROVIDER_BUSY with retryAfterSeconds")
    void testProviderBusy() {
        AllProvidersFailed ex = new AllProvidersFailed("Rate limit hit", true, 45L);
        ResponseEntity<ErrorResponse> response = handler.handleAllProvidersFailed(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PROVIDER_BUSY", response.getBody().error());
        assertEquals("The AI provider is currently busy. Please try again in a minute.", response.getBody().message());
        assertEquals(45L, response.getBody().retryAfterSeconds());
    }

    @Test
    @DisplayName("AllProvidersFailed with unavailable maps to 503 AI_UNAVAILABLE")
    void testAiUnavailable() {
        AllProvidersFailed ex = new AllProvidersFailed("Server timeout", false, null);
        ResponseEntity<ErrorResponse> response = handler.handleAllProvidersFailed(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AI_UNAVAILABLE", response.getBody().error());
        assertEquals("The AI service is temporarily unavailable. Please try again later.", response.getBody().message());
        assertNull(response.getBody().retryAfterSeconds());
    }

    @Test
    @DisplayName("BadModelOutputException and exhausted fallbacks map to 502 BAD_MODEL_OUTPUT")
    void testBadModelOutput() {
        BadModelOutputException ex = new BadModelOutputException("Could not parse JSON");
        ResponseEntity<ErrorResponse> response = handler.handleBadModelOutput(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_MODEL_OUTPUT", response.getBody().error());
        assertEquals("The AI model returned an unparseable or invalid response. Please try again.", response.getBody().message());
    }

    @Test
    @DisplayName("InvalidModelOutputException maps to 502 BAD_MODEL_OUTPUT")
    void testInvalidModelOutputException() {
        InvalidModelOutputException ex = new InvalidModelOutputException("Malformed schema");
        ResponseEntity<ErrorResponse> response = handler.handleBadModelOutput(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_MODEL_OUTPUT", response.getBody().error());
    }
}
