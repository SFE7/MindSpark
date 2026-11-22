package com.mindspark.subscription.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.mindspark.subscription.dto.ErrorResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPlanTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPlanTransition(InvalidPlanTransitionException ex) {
        log.warn("Invalid plan transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PLAN_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(DowngradeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleDowngradeNotSupported(DowngradeNotSupportedException ex) {
        log.warn("Downgrade not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("DOWNGRADE_NOT_SUPPORTED", ex.getMessage()));
    }

    @ExceptionHandler(AlreadySubscribedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadySubscribed(AlreadySubscribedException ex) {
        log.warn("Already subscribed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ALREADY_SUBSCRIBED", ex.getMessage()));
    }

    @ExceptionHandler(AlreadyOnPlanException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyOnPlan(AlreadyOnPlanException ex) {
        log.warn("Already on plan: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ALREADY_ON_PLAN", ex.getMessage()));
    }

    @ExceptionHandler(NoActiveSubscriptionException.class)
    public ResponseEntity<ErrorResponse> handleNoActiveSubscription(NoActiveSubscriptionException ex) {
        log.warn("No active subscription: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NO_ACTIVE_SUBSCRIPTION", ex.getMessage()));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlanNotFound(PlanNotFoundException ex) {
        log.warn("Plan not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PLAN_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenAccess(ForbiddenAccessException ex) {
        log.warn("Forbidden access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(PlanCodeExistsException.class)
    public ResponseEntity<ErrorResponse> handlePlanCodeExists(PlanCodeExistsException ex) {
        log.warn("Plan code exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("PLAN_CODE_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidPlanUpdateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPlanUpdate(InvalidPlanUpdateException ex) {
        log.warn("Invalid plan update: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PLAN_UPDATE", ex.getMessage()));
    }

    @ExceptionHandler(KeycloakAdminNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakAdminNotConfigured(KeycloakAdminNotConfiguredException ex) {
        log.warn("Keycloak admin not configured: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("KEYCLOAK_ADMIN_NOT_CONFIGURED", ex.getMessage()));
    }

    @ExceptionHandler(KeycloakRoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakRoleNotFound(KeycloakRoleNotFoundException ex) {
        log.warn("Keycloak role not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("KEYCLOAK_ROLE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AdminAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAdminAlreadyExists(AdminAlreadyExistsException ex) {
        log.warn("Admin already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ADMIN_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler({
            com.mindspark.subscription.polar.exception.PolarUnavailableException.class,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException.class
    })
    public ResponseEntity<ErrorResponse> handlePolarUnavailable(Exception ex) {
        log.error("Polar API unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("PAYMENT_PROVIDER_UNAVAILABLE", "Payment provider is temporarily unavailable. Please retry shortly."));
    }

    @ExceptionHandler(com.mindspark.subscription.polar.exception.CheckoutOwnershipMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCheckoutOwnershipMismatch(com.mindspark.subscription.polar.exception.CheckoutOwnershipMismatchException ex) {
        log.warn("Checkout ownership mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("CHECKOUT_OWNERSHIP_MISMATCH", ex.getMessage()));
    }

    @ExceptionHandler(com.mindspark.subscription.polar.exception.PaymentNotCompletedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotCompleted(com.mindspark.subscription.polar.exception.PaymentNotCompletedException ex) {
        log.warn("Payment not completed in Polar: status={}", ex.getPolarStatus());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("PAYMENT_NOT_COMPLETED", ex.getMessage(), ex.getPolarStatus()));
    }

    @ExceptionHandler(com.mindspark.subscription.polar.exception.InvalidReturnOriginException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReturnOrigin(com.mindspark.subscription.polar.exception.InvalidReturnOriginException ex) {
        log.warn("Invalid return origin: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_RETURN_ORIGIN", ex.getMessage()));
    }

    @ExceptionHandler(com.mindspark.subscription.polar.exception.UnknownPolarProductException.class)
    public ResponseEntity<ErrorResponse> handleUnknownPolarProduct(com.mindspark.subscription.polar.exception.UnknownPolarProductException ex) {
        log.warn("Unknown Polar product: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("UNKNOWN_POLAR_PRODUCT", ex.getMessage()));
    }

    @ExceptionHandler(com.mindspark.subscription.polar.exception.PolarClientException.class)
    public ResponseEntity<ErrorResponse> handlePolarClient(com.mindspark.subscription.polar.exception.PolarClientException ex) {
        log.warn("Polar client error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("PAYMENT_PROVIDER_ERROR", ex.getMessage()));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled subscription service exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred processing your subscription."));
    }
}
