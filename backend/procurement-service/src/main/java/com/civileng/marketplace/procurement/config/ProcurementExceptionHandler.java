package com.civileng.marketplace.procurement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * The two mappings procurement needs beyond the platform's: an action the document's current
 * state does not allow (approve an order already issued) is a 409, and so is a duplicate that the
 * database caught (a second invoice with the same number).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ProcurementExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return error(ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DataIntegrityViolationException ex) {
        log.warn("Integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error("That already exists");
    }

    private static ResponseEntity<Map<String, Object>> error(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", message,
                "status", 409, "timestamp", System.currentTimeMillis()));
    }
}
