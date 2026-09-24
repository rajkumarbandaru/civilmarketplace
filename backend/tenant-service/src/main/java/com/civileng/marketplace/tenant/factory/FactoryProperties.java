package com.civileng.marketplace.tenant.factory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Platform Factory settings, bound from {@code platform.factory.*}.
 *
 * @param requiredServices every service that must acknowledge a new tenant's storage before it
 *                         goes ACTIVE — each one running the tenant runtime
 * @param appUrlTemplate   where a tenant's app lives, {@code {subdomain}} substituted; the owner's
 *                         invitation link points there
 */
@ConfigurationProperties(prefix = "platform.factory")
public record FactoryProperties(List<String> requiredServices, Duration schemaTimeout, int maxAttempts,
                                String appUrlTemplate) {

    public FactoryProperties {
        requiredServices = requiredServices == null || requiredServices.isEmpty() ? List.of(
                "admin-service", "audit-service", "auth-service", "booking-service", "media-service",
                "messaging-service", "notification-service", "payment-service", "procurement-service", "project-service",
                "review-service", "search-service", "support-service", "user-service") : List.copyOf(requiredServices);
        schemaTimeout = schemaTimeout == null ? Duration.ofMinutes(10) : schemaTimeout;
        maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
        appUrlTemplate = appUrlTemplate == null || appUrlTemplate.isBlank()
                ? "http://{subdomain}.localhost:3000" : appUrlTemplate;
    }

    public String appUrl(String subdomain) {
        return appUrlTemplate.replace("{subdomain}", subdomain);
    }
}
