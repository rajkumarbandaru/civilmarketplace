package com.civileng.marketplace.tenant.common;

public final class TenantTopics {

    /** Tenant lifecycle events, published by tenant-service and consumed by every tenanted service. */
    public static final String TENANT_EVENTS = "tenant.events";

    private TenantTopics() {
    }
}
