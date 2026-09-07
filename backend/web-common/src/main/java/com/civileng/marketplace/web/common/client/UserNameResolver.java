package com.civileng.marketplace.web.common.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User id to display name, cached, with a placeholder when auth-service cannot answer.
 *
 * <p>booking-service and payment-service each carried an identical copy of this class. Both
 * decorate a booking or a payment with the counterparty's name for a screen, and neither can fail
 * the whole read because a name lookup did — hence the placeholder rather than an exception.
 *
 * <p>The cache is per-instance and TTL'd at five minutes. A stale display name for a few minutes
 * is not worth a shared cache and its invalidation; nothing here is used for authorisation.
 *
 * <p>Not a {@code @Service}: it is created by {@link SharedClientConfiguration} only where a
 * service actually has the Feign client, so putting the class on the classpath does not oblige a
 * service to wire auth-service.
 */
@Slf4j
@RequiredArgsConstructor
public class UserNameResolver {

    private static final long CACHE_TTL_MS = 300_000;

    private final UserNameClient userNameClient;

    private final ConcurrentHashMap<Long, CachedUser> nameCache = new ConcurrentHashMap<>();

    @CircuitBreaker(name = "userNameResolver", fallbackMethod = "resolveFallback")
    public ResolvedUser resolve(Long userId) {
        if (userId == null) {
            return new ResolvedUser(null, null, null, false);
        }

        CachedUser cached = nameCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached.cachedAt() < CACHE_TTL_MS) {
            return new ResolvedUser(cached.name(), cached.email(), cached.role(), true);
        }

        try {
            Map<String, Object> response = userNameClient.getUserName(userId).getBody();

            if (response != null && Boolean.TRUE.equals(response.get("exists"))) {
                String name = (String) response.get("name");
                String email = (String) response.get("email");
                String role = (String) response.get("role");

                nameCache.put(userId,
                        new CachedUser(name, email, role, System.currentTimeMillis()));
                return new ResolvedUser(name, email, role, true);
            }
        } catch (Exception e) {
            log.debug("Failed to resolve user name for {}: {}", userId, e.getMessage());
        }

        // Deliberately not cached: a failed lookup must be retried, not remembered.
        return new ResolvedUser("User #" + userId, null, null, false);
    }

    @SuppressWarnings("unused") // resolve()'s circuit-breaker fallback, resolved by name
    private ResolvedUser resolveFallback(Long userId, Throwable t) {
        log.warn("Fallback resolving user name for {}: {}", userId, t.getMessage());
        return new ResolvedUser("User #" + userId, null, null, false);
    }

    /** {@code resolved} tells a caller whether {@code name} is real or the placeholder. */
    public record ResolvedUser(String name, String email, String role, boolean resolved) {
    }

    private record CachedUser(String name, String email, String role, long cachedAt) {
    }
}
