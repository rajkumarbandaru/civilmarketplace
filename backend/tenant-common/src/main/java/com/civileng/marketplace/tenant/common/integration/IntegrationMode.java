package com.civileng.marketplace.tenant.common.integration;

/** Whose provider account a tenant's integration runs on. */
public enum IntegrationMode {

    /** The tenant's own account and credentials ("bring your own"). */
    BYO,

    /**
     * The platform's account, used on the tenant's behalf — the tenant's sender name and branding,
     * the platform's credentials. Also what a tenant with no row of its own gets.
     */
    PLATFORM_SHARED
}
