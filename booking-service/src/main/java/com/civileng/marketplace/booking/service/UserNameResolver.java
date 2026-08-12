package com.civileng.marketplace.booking.service;

import com.civileng.marketplace.booking.client.UserServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserNameResolver {

    private final UserServiceClient userServiceClient;

    // Simple in-memory cache to avoid redundant Feign calls
    private final ConcurrentHashMap<Long, CachedUser> nameCache = new ConcurrentHashMap<>();

    private static final long CACHE_TTL_MS = 300_000; // 5 minutes

    @CircuitBreaker(name = "userNameResolver", fallbackMethod = "resolveFallback")
    public ResolvedUser resolve(Long userId) {
        if (userId == null) {
            return new ResolvedUser(null, null, null, false);
        }

        // Check cache first
        CachedUser cached = nameCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MS) {
            return new ResolvedUser(cached.name, cached.email, cached.role, true);
        }

        // Fetch from auth-service
        try {
            Map<String, Object> response = userServiceClient.getUserName(userId).getBody();
            if (response != null && Boolean.TRUE.equals(response.get("exists"))) {
                String name = (String) response.get("name");
                String email = (String) response.get("email");
                String role = (String) response.get("role");

                // Update cache
                nameCache.put(userId, new CachedUser(name, email, role, System.currentTimeMillis()));

                return new ResolvedUser(name, email, role, true);
            }
        } catch (Exception e) {
            log.debug("Failed to resolve user name for {}: {}", userId, e.getMessage());
        }

        // Fallback
        return new ResolvedUser("User #" + userId, null, null, false);
    }

    private ResolvedUser resolveFallback(Long userId, Throwable t) {
        log.warn("Fallback resolving user name for {}: {}", userId, t.getMessage());
        return new ResolvedUser("User #" + userId, null, null, false);
    }

    public record ResolvedUser(String name, String email, String role, boolean resolved) {}

    private record CachedUser(String name, String email, String role, long cachedAt) {}
}
