package com.civileng.marketplace.tenant.model;

/**
 * Where a tenant is in its life (architecture 03 §4). Only {@link com.civileng.marketplace.tenant.service.TenantLifecycle}
 * moves a tenant between these.
 */
public enum TenantStatus {

    /** Created from the wizard: key and subdomain reserved, nothing provisioned, no traffic. */
    DRAFT,

    /** Published; services are building its storage. No traffic until every one has acknowledged. */
    PROVISIONING,

    /** A provisioning step failed after its retries. Retry, or discard back to a draft. */
    PROVISIONING_FAILED,

    /** Serving traffic; its schemas exist in every service. */
    ACTIVE,

    /**
     * Live but read-only for a few seconds while its data is moved between clusters or restored:
     * the gateway serves reads and refuses writes with 503 and Retry-After. Only a move or a
     * restore puts a tenant here, and takes it out again.
     */
    MAINTENANCE,

    /** Reachable domain, but the gateway refuses requests — non-payment, or an operator hold. */
    SUSPENDED,

    /** Closed. Schemas are retained for the contractual window; nothing routes here. */
    ARCHIVED
}
