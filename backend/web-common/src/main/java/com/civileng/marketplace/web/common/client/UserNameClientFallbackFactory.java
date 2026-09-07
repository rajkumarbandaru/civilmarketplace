package com.civileng.marketplace.web.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Answers with a placeholder name when auth-service is unreachable.
 *
 * <p>Declared as a bean by {@link SharedClientConfiguration}, not annotated {@code @Component}:
 * this package sits outside every service's component scan, so a stereotype annotation here would
 * be silently ignored and Feign would fail to resolve the fallback at runtime rather than at
 * compile time.
 *
 * <p>{@code exists: false} is the important part of the payload: a name that could not be resolved
 * must be distinguishable from one that genuinely reads "User #42", or a caller will cache the
 * placeholder as though it were the real thing.
 */
@Slf4j
public class UserNameClientFallbackFactory implements FallbackFactory<UserNameClient> {

    @Override
    public UserNameClient create(Throwable cause) {
        log.warn("auth-service unavailable, falling back for user name resolution: {}",
                cause.getMessage());
        return userId -> ResponseEntity.ok(Map.of(
                "success", false,
                "userId", userId,
                "name", "User #" + userId,
                "exists", false
        ));
    }
}
