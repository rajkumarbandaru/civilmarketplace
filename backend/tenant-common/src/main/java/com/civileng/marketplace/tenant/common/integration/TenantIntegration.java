package com.civileng.marketplace.tenant.common.integration;

import java.util.Map;

/**
 * One tenant's decrypted integration row, as the store hands it to the resolver.
 *
 * <p>{@code toString} is overridden so the secrets can never reach a log line through an
 * accidental {@code log.info("{}", integration)}.
 */
public record TenantIntegration(
        String tenantKey,
        IntegrationCapability capability,
        IntegrationMode mode,
        String provider,
        boolean enabled,
        Map<String, String> settings,
        Map<String, String> secrets,
        String webhookToken) {

    public TenantIntegration {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    @Override
    public String toString() {
        return "TenantIntegration[" + tenantKey + "/" + capability.key() + " " + mode
                + " provider=" + provider + " enabled=" + enabled
                + " settings=" + settings.keySet() + " secrets=" + secrets.keySet() + "]";
    }
}
