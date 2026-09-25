package com.civileng.marketplace.analytics.cdc;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which source tables feed the warehouse, and whose they are. The tenant comes from the schema
 * name ({@code civil_engineer_bookings_acme} → {@code acme}) — the same rule every service uses to
 * place a tenant's data, so a row can only ever be filed under the tenant whose schema it lives in.
 * Restore side schemas ({@code …__r120501}, {@code …__pre…}) and anything else are ignored.
 */
public final class TrackedTables {

    public enum Fact { BOOKINGS, PAYMENTS, PURCHASE_ORDERS }

    public record Source(String schemaPrefix, String table, Fact fact) { }

    public static final Map<String, Source> BY_PREFIX = Map.of(
            "civil_engineer_bookings", new Source("civil_engineer_bookings", "bookings", Fact.BOOKINGS),
            "civil_engineer_payments", new Source("civil_engineer_payments", "payments", Fact.PAYMENTS),
            "civil_engineer_procurement", new Source("civil_engineer_procurement", "purchase_orders", Fact.PURCHASE_ORDERS));

    private static final Pattern SCHEMA = Pattern.compile("^(civil_engineer_[a-z]+)_([a-z0-9]{1,31})$");

    private TrackedTables() {
    }

    public record Match(String tenantKey, Fact fact) { }

    public static Optional<Match> match(String schema, String table) {
        if (schema == null || table == null) {
            return Optional.empty();
        }
        Matcher m = SCHEMA.matcher(schema);
        if (!m.matches()) {
            return Optional.empty();
        }
        Source s = BY_PREFIX.get(m.group(1));
        return s == null || !s.table().equals(table) ? Optional.empty() : Optional.of(new Match(m.group(2), s.fact()));
    }
}
