package com.civileng.marketplace.tenant.common.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Serves {@link IntegrationNotConfiguredException} as 409 in the platform's error shape, with a
 * stable {@code code} the frontend can key a "ask your administrator to connect X" message on.
 * Ordered first so a service's catch-all {@code Exception} handler does not turn it into a 500.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IntegrationExceptionHandler {

    @ExceptionHandler(IntegrationNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> notConfigured(IntegrationNotConfiguredException ex) {
        log.warn("Tenant {} has no {} integration configured", ex.tenantKey(), ex.capability().key());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "code", "INTEGRATION_NOT_CONFIGURED",
                "capability", ex.capability().key(),
                "message", ex.getMessage(),
                "status", HttpStatus.CONFLICT.value(),
                "timestamp", System.currentTimeMillis()));
    }
}
