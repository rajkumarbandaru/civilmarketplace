package com.civileng.marketplace.tenant.common;

public final class TenantTopics {

    /** Tenant lifecycle events, published by tenant-service and consumed by every tenanted service. */
    public static final String TENANT_EVENTS = "tenant.events";

    /**
     * Each service's report that it has (or has failed to) provision a tenant: what tenant-service's
     * provisioning saga waits on before a new tenant goes ACTIVE. JSON: {@link TenantProvisioningAck}.
     */
    public static final String TENANT_PROVISIONED = "tenant.provisioned";

    private TenantTopics() {
    }
}
