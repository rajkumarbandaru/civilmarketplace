package com.civileng.marketplace.tenant.model;

public enum TenantStatus {

    /** Serving traffic; its schemas exist in every service. */
    ACTIVE,

    /** Created but not yet provisioned across services. */
    PENDING,

    /** Reachable domain, but the gateway refuses requests — non-payment, or an operator hold. */
    SUSPENDED,

    /** Closed. Schemas are retained for the contractual window; nothing routes here. */
    ARCHIVED
}
