package com.civileng.marketplace.web.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps a lost optimistic-lock race to 409.
 *
 * <p>Separate from {@link PlatformExceptionHandler} because it names a class from {@code
 * spring-orm}, which search-service does not have — its store is Elasticsearch. Spring resolves
 * {@code @ExceptionHandler} attributes eagerly, so a missing exception type is a startup failure
 * rather than a quietly inactive mapping; keeping it in its own {@code @ConditionalOnClass} bean
 * is what lets one module serve both kinds of service.
 *
 * <p>The conflict is the caller's to retry, not a server fault. The platform's one {@code @Version}
 * field today is project-service's budget ceiling, where the SRS wants last-write-wins but the
 * loser told rather than silently overwritten.
 */
@RestControllerAdvice
@Slf4j
public class OptimisticLockExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent edit rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("success", false,
                        "message", "This record was changed by someone else. Reload and try again.",
                        "status", HttpStatus.CONFLICT.value(),
                        "timestamp", System.currentTimeMillis()));
    }
}
