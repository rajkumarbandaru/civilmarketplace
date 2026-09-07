package com.civileng.marketplace.tenant.model;

/**
 * A deployable capability a tenant can have switched on. The key is what the gateway matches
 * routes against and what the frontend shell reads to build its menu, so it is stable API.
 */
public enum PlatformModule {

    // Horizontal — every tenant has these, whatever vertical it runs.
    AUTH("auth"),
    USERS("users"),
    PAYMENTS("payments"),
    NOTIFICATIONS("notifications"),
    SUPPORT("support"),
    ADMIN("admin"),
    AUDIT("audit"),
    MESSAGING("messaging"),

    /**
     * Tenant administration itself. Held by the operator tenant alone, which is what keeps the
     * Tenants screen — and {@code /api/v1/tenants/**} — out of a customer tenant's console rather
     * than merely 403ing them after they click it.
     */
    TENANT_ADMIN("tenantadmin"),

    // Civil-engineering marketplace vertical.
    BOOKINGS("bookings"),
    PROJECTS("projects"),
    REVIEWS("reviews"),
    SEARCH("search"),

    // Fee-collection vertical (hostels, institutions).
    RESIDENTS("residents"),
    FEE_PLANS("feeplans"),
    INVOICES("invoices"),
    COLLECTIONS("collections"),

    // Property vertical (Bhoomi360).
    PROPERTIES("properties"),
    LISTINGS("listings"),
    LEASES("leases"),
    VALUATIONS("valuations"),
    LAND_RECORDS("landrecords");

    private final String key;

    PlatformModule(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static PlatformModule fromKey(String key) {
        for (PlatformModule module : values()) {
            if (module.key.equals(key)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Unknown module '" + key + "'");
    }
}
