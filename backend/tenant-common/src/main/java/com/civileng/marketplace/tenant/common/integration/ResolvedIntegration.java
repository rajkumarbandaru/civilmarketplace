package com.civileng.marketplace.tenant.common.integration;

import java.util.Map;

/**
 * Which credentials an adapter must use for the operation in hand.
 *
 * <ul>
 *   <li>{@link Source#TENANT} — the tenant's own account: use {@link #settings} and {@link #secrets}.
 *   <li>{@link Source#PLATFORM_SHARED} — the platform's account on the tenant's behalf: use the
 *       service's own configured credentials, but the tenant's {@link #settings} (sender name).
 *   <li>{@link Source#PLATFORM} — the operator tenant's own business (billing tenants, Super Admin
 *       OTPs): the service's configured credentials, nothing of any tenant's.
 * </ul>
 */
public record ResolvedIntegration(
        String tenantKey,
        IntegrationCapability capability,
        Source source,
        String provider,
        Map<String, String> settings,
        Map<String, String> secrets) {

    public enum Source { TENANT, PLATFORM_SHARED, PLATFORM }

    public ResolvedIntegration {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    /** True when the adapter must use the service's own (platform) credentials. */
    public boolean usesPlatformCredentials() {
        return source != Source.TENANT;
    }

    public String setting(String key) {
        return settings.get(key);
    }

    public String secret(String key) {
        return secrets.get(key);
    }

    @Override
    public String toString() {
        return "ResolvedIntegration[" + tenantKey + "/" + capability.key() + " " + source
                + " provider=" + provider + "]";
    }
}
