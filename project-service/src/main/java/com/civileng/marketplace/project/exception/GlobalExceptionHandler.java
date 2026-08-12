package com.civileng.marketplace.project.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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

    /**
     * Two owners editing the same project at once. The SRS wants last-write-wins, not a silent
     * overwrite — so the loser is told to re-read and retry rather than having its write vanish.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent project edit rejected: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT,
                "This project was modified by someone else — reload and try again");
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String msg) {
        return ResponseEntity.status(status)
                .body(Map.of("success", false, "message", msg,
                        "status", status.value(), "timestamp", System.currentTimeMillis()));
    }
}
