package com.civileng.marketplace.tenant.common.integration;

import com.civileng.marketplace.tenant.common.TenantContext;

import java.util.Optional;

/**
 * The one place an adapter asks "whose credentials do I use for this?".
 *
 * <p>The answer always comes from the tenant bound to the current thread, never from a request
 * parameter. A tenant with no row of its own runs on the platform's account (the default every
 * tenant starts with); a tenant that brought its own account uses that; a tenant that switched the
 * capability off gets {@link IntegrationNotConfiguredException}. The operator tenant always
 * resolves to the platform's own credentials.
 */
public class TenantIntegrationResolver {

    private final TenantIntegrationStore store;
    private final String operatorTenant;

    public TenantIntegrationResolver(TenantIntegrationStore store, String operatorTenant) {
        this.store = store;
        this.operatorTenant = operatorTenant;
    }

    /** Resolves for the current tenant, or throws when it has no usable integration. */
    public ResolvedIntegration require(IntegrationCapability capability) {
        String tenantKey = TenantContext.require();
        return find(tenantKey, capability)
                .orElseThrow(() -> new IntegrationNotConfiguredException(tenantKey, capability));
    }

    /** Resolves for the current tenant; empty means "not configured" and callers decide. */
    public Optional<ResolvedIntegration> find(IntegrationCapability capability) {
        return find(TenantContext.require(), capability);
    }

    public Optional<ResolvedIntegration> find(String tenantKey, IntegrationCapability capability) {
        if (operatorTenant.equals(tenantKey)) {
            return Optional.of(new ResolvedIntegration(tenantKey, capability,
                    ResolvedIntegration.Source.PLATFORM, null, null, null));
        }
        Optional<TenantIntegration> row = store.find(tenantKey, capability);
        if (row.isEmpty()) {
            return Optional.of(platformDefault(tenantKey, capability, java.util.Map.of()));
        }
        return row.filter(TenantIntegration::enabled).map(r -> toResolved(r, capability));
    }

    /** The tenant an inbound webhook belongs to, with that tenant's credentials to verify it. */
    public Optional<ResolvedIntegration> forWebhook(IntegrationCapability capability, String token) {
        return store.findByWebhookToken(capability, token)
                .filter(TenantIntegration::enabled)
                .filter(row -> row.mode() == IntegrationMode.BYO)
                .map(row -> toResolved(row, capability));
    }

    private ResolvedIntegration toResolved(TenantIntegration row, IntegrationCapability capability) {
        if (row.mode() == IntegrationMode.PLATFORM_SHARED) {
            return platformDefault(row.tenantKey(), capability, row.settings());
        }
        return new ResolvedIntegration(row.tenantKey(), capability,
                ResolvedIntegration.Source.TENANT, row.provider(), row.settings(), row.secrets());
    }

    /** The platform's account on the tenant's behalf, with the tenant's own labels (sender name). */
    private static ResolvedIntegration platformDefault(String tenantKey, IntegrationCapability capability,
                                                       java.util.Map<String, String> settings) {
        return new ResolvedIntegration(tenantKey, capability, ResolvedIntegration.Source.PLATFORM_SHARED,
                null, settings, null);
    }
}
