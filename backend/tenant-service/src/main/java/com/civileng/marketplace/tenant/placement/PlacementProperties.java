package com.civileng.marketplace.tenant.placement;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tenant placement and moves, bound from {@code platform.placement.*}.
 *
 * @param schemaPrefixes every data service's schema prefix → the service, whose acknowledgement a
 *                       move waits for. A tenant's data is {@code <prefix>_<tenantKey>} on its cluster.
 * @param drain          after pausing writes, how long in-flight requests are given to finish
 * @param ackTimeout     how long a move waits for every service to route to the new cluster
 * @param batchSize      rows per insert batch when copying
 * @param jdbcOptions    connection options for the copier's connections to each cluster
 */
@ConfigurationProperties(prefix = "platform.placement")
public record PlacementProperties(Map<String, String> schemaPrefixes, Duration drain, Duration ackTimeout, int batchSize,
                                  String jdbcOptions, String username, String password) {

    public static final Map<String, String> DEFAULT_PREFIXES = new LinkedHashMap<>();

    static {
        DEFAULT_PREFIXES.put("admin_db", "admin-service");
        DEFAULT_PREFIXES.put("civil_engineer_audit", "audit-service");
        DEFAULT_PREFIXES.put("civil_engineer_auth", "auth-service");
        DEFAULT_PREFIXES.put("civil_engineer_bookings", "booking-service");
        DEFAULT_PREFIXES.put("civil_engineer_media", "media-service");
        DEFAULT_PREFIXES.put("civil_engineer_messaging", "messaging-service");
        DEFAULT_PREFIXES.put("civil_engineer_notifications", "notification-service");
        DEFAULT_PREFIXES.put("civil_engineer_payments", "payment-service");
        DEFAULT_PREFIXES.put("civil_engineer_procurement", "procurement-service");
        DEFAULT_PREFIXES.put("civil_engineer_projects", "project-service");
        DEFAULT_PREFIXES.put("civil_engineer_reviews", "review-service");
        DEFAULT_PREFIXES.put("civil_engineer_support", "support-service");
        DEFAULT_PREFIXES.put("civil_engineer_users", "user-service");
    }

    public PlacementProperties {
        schemaPrefixes = schemaPrefixes == null || schemaPrefixes.isEmpty() ? DEFAULT_PREFIXES : schemaPrefixes;
        drain = drain == null ? Duration.ofSeconds(3) : drain;
        ackTimeout = ackTimeout == null ? Duration.ofMinutes(2) : ackTimeout;
        batchSize = batchSize <= 0 ? 500 : batchSize;
        jdbcOptions = jdbcOptions == null || jdbcOptions.isBlank()
                ? "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true" : jdbcOptions;
    }
}
