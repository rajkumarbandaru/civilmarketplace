package com.civileng.marketplace.tenant.common.integration;

import com.civileng.marketplace.tenant.common.TenantContext;

import java.util.Optional;

/**
 * The one place an adapter asks "whose credentials do I use for this?".
 *
 * <p>The answer always comes from the tenant bound to the current thread, never from a request
 * parameter. There is no silent fallback: a tenant with nothing configured gets
 * {@link IntegrationNotConfiguredException}, not the platform's account. Only the operator tenant
 * resolves to the platform's own credentials — that is the Super Admin's separate set.
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
        return store.find(tenantKey, capability)
                .filter(TenantIntegration::enabled)
                .flatMap(row -> toResolved(row, capability));
    }

    /** The tenant an inbound webhook belongs to, with that tenant's credentials to verify it. */
    public Optional<ResolvedIntegration> forWebhook(IntegrationCapability capability, String token) {
        return store.findByWebhookToken(capability, token)
                .filter(TenantIntegration::enabled)
                .filter(row -> row.mode() == IntegrationMode.BYO)
                .flatMap(row -> toResolved(row, capability));
    }

    private Optional<ResolvedIntegration> toResolved(TenantIntegration row,
                                                     IntegrationCapability capability) {
        if (row.mode() == IntegrationMode.PLATFORM_SHARED) {
            // A row written before a capability stopped allowing sharing, or written around the
            // API, must not smuggle a tenant onto the platform's merchant or DLT account.
            if (!capability.allowsPlatformShared()) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedIntegration(row.tenantKey(), capability,
                    ResolvedIntegration.Source.PLATFORM_SHARED, null, row.settings(), null));
        }
        return Optional.of(new ResolvedIntegration(row.tenantKey(), capability,
                ResolvedIntegration.Source.TENANT, row.provider(), row.settings(), row.secrets()));
    }
}
