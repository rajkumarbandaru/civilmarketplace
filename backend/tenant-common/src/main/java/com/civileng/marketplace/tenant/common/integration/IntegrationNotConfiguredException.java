package com.civileng.marketplace.tenant.common.integration;

/**
 * A tenant tried to use a capability it has no provider account for.
 *
 * <p>Thrown instead of falling back to the platform's credentials: charging a tenant's customer
 * through the platform's merchant account, or sending its SMS from the platform's DLT header, is
 * exactly the mistake the per-tenant rule exists to prevent. Served as 409 by
 * {@code IntegrationExceptionHandler}.
 */
public class IntegrationNotConfiguredException extends RuntimeException {

    private final String tenantKey;
    private final IntegrationCapability capability;

    public IntegrationNotConfiguredException(String tenantKey, IntegrationCapability capability) {
        super(capability.key() + " is not configured for this tenant");
        this.tenantKey = tenantKey;
        this.capability = capability;
    }

    public String tenantKey() {
        return tenantKey;
    }

    public IntegrationCapability capability() {
        return capability;
    }
}
