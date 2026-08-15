package com.civileng.marketplace.admin.uiconfig.service;

import com.civileng.marketplace.admin.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The platform's role catalogue, read from auth-service — roles live in that service's schema,
 * so keeping a second copy here would silently drift the moment a role is added.
 * <p>
 * Held for a short window rather than fetched per call: the workspace list resolves every role's
 * menu, so an uncached read would be one Feign round-trip per row. If auth-service is
 * unreachable, the last good answer is served rather than failing the console — a stale role list
 * is recoverable, a console that will not load is not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoleDirectory {

    private static final Duration TTL = Duration.ofMinutes(2);

    private final AuthServiceClient authServiceClient;

    private volatile Map<String, Long> cachedCounts = Map.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    /** Every role name on the platform, in the order auth-service returns them. */
    public List<String> allRoles() {
        return List.copyOf(counts().keySet());
    }

    public boolean exists(String role) {
        return counts().containsKey(role);
    }

    /** Live user count per role. Roles with no users are present with a count of zero. */
    public Map<String, Long> userCounts() {
        return counts();
    }

    /**
     * Drops the cached catalogue so the next read goes to auth-service. Called after this service
     * creates a role: without it the new workspace would be missing from the console for up to
     * the TTL, which reads as the save having failed.
     */
    public void invalidate() {
        fetchedAt = Instant.EPOCH;
    }

    private Map<String, Long> counts() {
        if (Duration.between(fetchedAt, Instant.now()).compareTo(TTL) < 0 && !cachedCounts.isEmpty()) {
            return cachedCounts;
        }
        try {
            Map<String, Object> body = authServiceClient.getRoles().getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = body == null
                    ? List.of() : (List<Map<String, Object>>) body.getOrDefault("data", new ArrayList<>());

            Map<String, Long> fresh = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object name = row.get("name");
                Object count = row.get("userCount");
                if (name != null) {
                    fresh.put(name.toString(), count instanceof Number n ? n.longValue() : 0L);
                }
            }
            if (!fresh.isEmpty()) {
                // unmodifiableMap over the LinkedHashMap, not Map.copyOf — the latter is
                // unordered, and auth-service already returns the roles alphabetically.
                cachedCounts = java.util.Collections.unmodifiableMap(fresh);
                // Only a successful, non-empty fetch resets the clock, so a failing auth-service
                // is retried on the next call instead of every two minutes.
                fetchedAt = Instant.now();
            }
        } catch (Exception e) {
            log.warn("Could not refresh the role catalogue from auth-service, using the last known "
                    + "list of {} roles: {}", cachedCounts.size(), e.getMessage());
        }
        return cachedCounts;
    }
}
