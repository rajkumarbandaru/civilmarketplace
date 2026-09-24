package com.civileng.marketplace.tenant.common.integration;

/** Whose provider account a tenant's integration runs on. */
public enum IntegrationMode {

    /** The tenant's own account and credentials ("bring your own"). */
    BYO,

    /**
     * The platform's account, used on the tenant's behalf — the tenant's sender name and branding,
     * the platform's credentials. Only legal where {@link IntegrationCapability#allowsPlatformShared}.
     */
    PLATFORM_SHARED
}
