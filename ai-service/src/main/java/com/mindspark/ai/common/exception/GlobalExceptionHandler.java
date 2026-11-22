package com.mindspark.ai.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.mindspark.ai.common.dto.ErrorResponse;
import com.mindspark.ai.provider.exception.AllProvidersFailed;
import com.mindspark.ai.provider.exception.InvalidModelOutputException;
import com.mindspark.ai.provider.exception.ProviderException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed for request parameters");

        log.warn("Method argument validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        log.warn("Handler method validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Validation failed for request parameters"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Validation failed: " + ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(DailyLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleDailyLimitReached(DailyLimitReachedException ex) {
        log.info("Daily limit reached: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("DAILY_LIMIT_REACHED", ex.getMessage()));
    }

    @ExceptionHandler(AllProvidersFailed.class)
    public ResponseEntity<ErrorResponse> handleAllProvidersFailed(AllProvidersFailed ex) {
        if (ex.isRateLimited()) {
            log.warn("All providers rate-limited, returning PROVIDER_BUSY");
            Long retryAfter = ex.getRetryAfterSeconds() != null ? ex.getRetryAfterSeconds() : 60L;
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("PROVIDER_BUSY", "The AI provider is currently busy. Please try again in a minute.", retryAfter));
        }

        log.error("All providers failed or unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AI_UNAVAILABLE", "The AI service is temporarily unavailable. Please try again later."));
    }

    @ExceptionHandler({BadModelOutputException.class, InvalidModelOutputException.class})
    public ResponseEntity<ErrorResponse> handleBadModelOutput(Exception ex) {
        log.warn("Model output parsing or schema validation failed after all fallbacks: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("BAD_MODEL_OUTPUT", "The AI model returned an unparseable or invalid response. Please try again."));
    }

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ErrorResponse> handleProviderException(ProviderException ex) {
        log.error("Unhandled provider exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AI_UNAVAILABLE", "The AI service is temporarily unavailable. Please try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled server exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AI_UNAVAILABLE", "An unexpected error occurred while processing your AI request."));
    }
}
