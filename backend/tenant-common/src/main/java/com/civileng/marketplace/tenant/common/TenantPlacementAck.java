package com.civileng.marketplace.tenant.common;

/**
 * "This service now sends tenant X's queries to cluster Y (placement epoch N), and treats it as
 * {@code status}." Sent as JSON text on {@link TenantTopics#TENANT_PLACEMENT_ACKS}; a move waits
 * for one from every service that stores the tenant's data — that writes are paused before its
 * final copy, and that queries go to the new cluster before writes resume.
 */
public record TenantPlacementAck(String tenantKey, String service, String clusterId, long epoch, String status, long at) { }
