package com.civileng.marketplace.web.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The platform's error shape, in one place.
 *
 * <p>This replaces ten byte-for-byte copies of the same class, which differed only in their
 * package and in which local {@code AccessDeniedException} they imported. Three services keep
 * their own handler because their response body is not this shape and changing it would break
 * their callers: auth-service and booking-service return an {@code ErrorResponse}, admin-service
 * an {@code ApiResponse}.
 *
 * <p>The union of what those ten had is what ships here, so adopting it never removes a mapping.
 * Two are new to most of them: {@link NoSuchElementException} was only handled by
 * notification-service and {@link ObjectOptimisticLockingFailureException} only by
 * project-service — everywhere else both fell through to the catch-all and were served as 500,
 * which is wrong for a missing row and actively misleading for a lost concurrent write.
 *
 * <p>A service that needs a mapping this does not have adds its own {@code @RestControllerAdvice}
 * for that exception type; Spring prefers the most specific handler, so the two coexist without
 * this class needing to know.
 */
@RestControllerAdvice
@Slf4j
public class PlatformExceptionHandler {
    /**
     * A path variable or query parameter that will not convert — {@code /bookings/nope} against a
     * {@code Long} id. The caller sent a bad request; reporting it as 500 sends them looking for a
     * server fault that is not there. Same family as {@link NoResourceFoundException} above.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Bad parameter '{}': {}", ex.getName(), ex.getValue());
        return buildError(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "message", "Validation failed",
                        "errors", errors));
    }

    /**
     * A path no handler is mapped to. Spring raises this rather than answering 404 itself, so
     * without an explicit mapping it lands on the catch-all below and a mistyped URL is reported
     * as a server fault. Every service inherited that from the copies this class replaces — it is
     * also why {@code /actuator/prometheus} answered 500 on the services that do not expose it,
     * which reads as an outage to anything scraping them.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No handler for {}", ex.getResourcePath());
        return buildError(HttpStatus.NOT_FOUND, "Not found");
    }

    /**
     * The message is fixed rather than {@code ex.getMessage()} on purpose: an unhandled exception's
     * message is as likely to be a SQL fragment or a hostname as anything a caller can act on.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String msg) {
        return ResponseEntity.status(status)
                .body(Map.of("success", false, "message", msg,
                        "status", status.value(), "timestamp", System.currentTimeMillis()));
    }
}
