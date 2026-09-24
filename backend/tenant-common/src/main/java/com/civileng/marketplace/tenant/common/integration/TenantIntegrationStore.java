package com.civileng.marketplace.tenant.common.integration;

import java.util.Optional;

/** Read side of the tenant integration table owned by tenant-service. */
public interface TenantIntegrationStore {

    Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability);

    /**
     * The integration an inbound provider webhook belongs to. Webhooks carry no tenant header and
     * no JWT, so the opaque token in their URL is the only trustworthy way back to a tenant.
     */
    Optional<TenantIntegration> findByWebhookToken(IntegrationCapability capability, String token);

    /** Drops cached rows so the next read sees a just-saved change. */
    void evict(String tenantKey);
}
