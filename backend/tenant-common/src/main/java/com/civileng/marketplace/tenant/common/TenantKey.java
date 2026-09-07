package com.civileng.marketplace.tenant.common;

import java.util.regex.Pattern;

/**
 * A tenant key is the tenant's subdomain label and also part of a schema name, so it has to be
 * safe in both places. Validation is strict and central: the key is concatenated into DDL when a
 * schema is provisioned, and no amount of downstream escaping makes an unvalidated identifier
 * safe there.
 */
public final class TenantKey {

    private static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9]{1,30}$");

    /** Keys that would collide with infrastructure hostnames rather than name a tenant. */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "www", "api", "admin", "app", "mail", "static", "assets", "cdn",
            "mysql", "redis", "kafka", "grafana", "prometheus", "public", "information"
    );

    private TenantKey() {
    }

    public static String normalise(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase();
        if (!VALID.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tenant key '" + raw + "' — 2-31 chars, lowercase letters and digits, "
                            + "must start with a letter");
        }
        if (RESERVED.contains(key)) {
            throw new IllegalArgumentException("Tenant key '" + key + "' is reserved");
        }
        return key;
    }

    public static boolean isValid(String raw) {
        try {
            normalise(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
