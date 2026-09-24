package com.civileng.marketplace.tenant.entitlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What can be entitled (architecture 08 §3). Owned by code: a feature exists because some code
 * path honours it.
 */
public final class FeatureCatalog {

    private FeatureCatalog() {
    }

    /** Every tenant has these whatever it bought: without them there is no working workspace. */
    public static final Set<String> BASE = Set.of("auth", "users", "payments", "notifications", "support",
            "admin", "audit", "messaging");

    /** Held by the operator tenant alone; no plan can sell it. */
    public static final String OPERATOR_ONLY = "tenantadmin";

    /** Numeric limits. Absent from an entitlement means unlimited. */
    public static final Map<String, String> LIMITS = new LinkedHashMap<>(Map.of(
            "staff.seats", "Staff users",
            "bookings.monthly", "Bookings per month",
            "media.storageMb", "Storage (MB)"));

    /** What can be bought on top of a plan: features it adds, and increments to limits. */
    public record AddOn(String key, String name, Set<String> features, Map<String, Long> increments) { }

    public static final Map<String, AddOn> ADD_ONS = new LinkedHashMap<>();

    static {
        add(new AddOn("projects", "Projects module", Set.of("projects"), Map.of()));
        add(new AddOn("landrecords", "Land records module", Set.of("landrecords"), Map.of()));
        add(new AddOn("procurement", "Procurement (B2B) module", Set.of("procurement"), Map.of()));
        add(new AddOn("bookings-5k", "+5,000 bookings a month", Set.of(), Map.of("bookings.monthly", 5000L)));
        add(new AddOn("seats-10", "+10 staff users", Set.of(), Map.of("staff.seats", 10L)));
    }

    private static void add(AddOn a) {
        ADD_ONS.put(a.key(), a);
    }

    public static boolean isLimit(String key) {
        return LIMITS.containsKey(key);
    }
}
