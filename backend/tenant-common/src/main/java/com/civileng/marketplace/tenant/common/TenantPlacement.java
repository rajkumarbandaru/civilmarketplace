package com.civileng.marketplace.tenant.common;

/**
 * Where a tenant's data lives: which MySQL cluster (host and port) its schemas are on, and its
 * status. The epoch increases every time the tenant is moved, which is how a service tells a move
 * from a re-read; MAINTENANCE means its writes are paused while it is moved or restored.
 */
public record TenantPlacement(String tenantKey, String clusterId, String host, int port, long epoch, String status) {

    public boolean inMaintenance() {
        return "MAINTENANCE".equals(status);
    }

    /** Same routing and same write state: nothing for this service to do or report. */
    boolean sameAs(TenantPlacement other) {
        return other != null && other.epoch == epoch && java.util.Objects.equals(other.status, status);
    }
}
