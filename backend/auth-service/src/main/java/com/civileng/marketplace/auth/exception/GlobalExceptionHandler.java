package com.civileng.marketplace.auth.exception;

import jakarta.validation.ConstraintViolationException;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * A path variable or query parameter that will not convert — {@code /bookings/nope} against a
     * {@code Long} id. The caller sent a bad request; reporting it as 500 sends them looking for a
     * server fault that is not there. Same family as {@link NoResourceFoundException} above.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Bad parameter '{}': {}", ex.getName(), ex.getValue());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    /**
     * A path no handler is mapped to. Spring raises this rather than answering 404 itself, so
     * without an explicit mapping a mistyped URL lands on the catch-all below and is reported as a
     * server fault — which is also why {@code /actuator/prometheus} answered 500 here, reading as
     * an outage to anything scraping this service.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No handler for {}", ex.getResourcePath());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not found");
    }

    /**
     * The shared 403 from {@code web-common}, served in this service's own body shape. Raised by
     * the staff-role gate, and by any handler that calls {@code StaffRoles.requireStaff}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** A caller whose {@code X-User-Role} header does not carry the role an endpoint requires. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(LockedException ex) {
        log.warn("Account locked: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.LOCKED, ex.getMessage());
    }

    /**
     * The unique constraints on {@code users.email} / {@code users.phone}. The service-layer
     * checks catch almost every duplicate, but two concurrent registrations can both pass the
     * check and race to insert — the database is what actually stops the second one, and the
     * caller deserves "already registered" rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DataIntegrityViolationException ex) {
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        String message = "Account already exists";
        if (detail.contains("phone")) {
            message = "Phone number already registered";
        } else if (detail.contains("email")) {
            message = "Email already registered";
        }
        log.warn("Duplicate account rejected: {}", detail);
        return buildErrorResponse(HttpStatus.CONFLICT, message);
    }

    /**
     * The OTP resend cooldown. Without this it fell through to the catch-all below and the
     * caller got a 500 "unexpected error" instead of the actual "please wait N seconds" —
     * the message is the whole point of the response, so it must reach the client intact.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(IllegalStateException ex) {
        log.warn("Rate limited: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Validation failed",
                        "errors", errors,
                        "timestamp", System.currentTimeMillis()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "success", false,
                        "message", message,
                        "status", status.value(),
                        "timestamp", System.currentTimeMillis()
                ));
    }
}
