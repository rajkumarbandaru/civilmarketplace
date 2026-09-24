package com.civileng.marketplace.tenant.common;

/**
 * One service's answer to "provision tenant X": {@code ok} once its schema is migrated and its
 * post-provision callbacks have run, or {@code ok=false} with the reason. Sent as JSON text on
 * {@link TenantTopics#TENANT_PROVISIONED}.
 */
public record TenantProvisioningAck(String tenantKey, String service, boolean ok, String error, long at) { }
