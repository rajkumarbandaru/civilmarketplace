package com.civileng.marketplace.web.common.client;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Hard plan limits, checked where the counted thing is created (architecture 08 §5–§6).
 *
 * <p>Limits are cached per tenant for 30 seconds, the platform's bound on entitlement staleness,
 * so an upgrade takes effect within that. If tenant-service cannot be asked, the check fails OPEN:
 * the limits enforced here (bookings a month) are commercial, not security, boundaries, and an
 * outage elsewhere must not stop a tenant taking business.
 */
@Slf4j
public class Quotas {

    static final Duration TTL = Duration.ofSeconds(30);

    private final EntitlementsClient client;
    private final Supplier<String> currentTenant;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Map<String, Long> limits, Instant at) { }

    public Quotas(EntitlementsClient client, Supplier<String> currentTenant, Clock clock) {
        this.client = client;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    /** The current tenant's limit, or empty when unlimited (or unknown). */
    public OptionalLong limit(String key) {
        String tenant = currentTenant.get();
        if (tenant == null) return OptionalLong.empty();
        Cached c = cache.get(tenant);
        if (c == null || c.at().plus(TTL).isBefore(clock.instant())) {
            try {
                EntitlementsClient.Entitlements e = client.mine();
                c = new Cached(e.limits() == null ? Map.of() : Map.copyOf(e.limits()), clock.instant());
                cache.put(tenant, c);
            } catch (RuntimeException ex) {
                log.warn("Could not read entitlements for '{}'; not enforcing {} ({})", tenant, key, ex.getMessage());
                return c == null ? OptionalLong.empty() : opt(c.limits().get(key));
            }
        }
        return opt(c.limits().get(key));
    }

    /**
     * Refuses the next unit when {@code used} has reached the limit.
     *
     * @param label how the limit reads to a person, e.g. "bookings this month"
     */
    public void require(String key, long used, String label) {
        OptionalLong limit = limit(key);
        if (limit.isPresent() && used >= limit.getAsLong()) {
            throw new QuotaExceededException(key, limit.getAsLong(), "This workspace's plan allows "
                    + limit.getAsLong() + " " + label + ", and that limit has been reached. "
                    + "Ask the platform to upgrade the plan or add capacity.");
        }
    }

    private static OptionalLong opt(Long v) {
        return v == null ? OptionalLong.empty() : OptionalLong.of(v);
    }
}
