package com.civileng.marketplace.tenant.model;

import java.util.EnumSet;
import java.util.Set;

import static com.civileng.marketplace.tenant.model.PlatformModule.*;

/**
 * The product a tenant is running. A vertical is only a starting module set — an operator can
 * add or remove modules per tenant afterwards — but it keeps onboarding a one-field decision
 * rather than a checklist of twelve.
 */
public enum Vertical {

    /** The original product: civil-engineering service marketplace. */
    CIVIL_MARKETPLACE(EnumSet.of(
            AUTH, USERS, PAYMENTS, NOTIFICATIONS, SUPPORT, ADMIN, AUDIT, MESSAGING,
            BOOKINGS, PROJECTS, REVIEWS, SEARCH)),

    /** Hostel / institutional fee collection: residents, fee plans, invoices, payment chasing. */
    FEE_COLLECTION(EnumSet.of(
            AUTH, USERS, PAYMENTS, NOTIFICATIONS, SUPPORT, ADMIN, AUDIT, MESSAGING,
            RESIDENTS, FEE_PLANS, INVOICES, COLLECTIONS)),

    /**
     * Property platform (Bhoomi360). Keeps SEARCH and REVIEWS from the marketplace vertical —
     * discovery and reputation are the same problem for a property listing as for a contractor —
     * and adds the property-specific record keeping.
     */
    PROPERTY(EnumSet.of(
            AUTH, USERS, PAYMENTS, NOTIFICATIONS, SUPPORT, ADMIN, AUDIT, MESSAGING,
            SEARCH, REVIEWS,
            PROPERTIES, LISTINGS, LEASES, VALUATIONS, LAND_RECORDS));

    private final Set<PlatformModule> defaultModules;

    Vertical(Set<PlatformModule> defaultModules) {
        this.defaultModules = defaultModules;
    }

    public Set<PlatformModule> defaultModules() {
        return defaultModules;
    }
}
