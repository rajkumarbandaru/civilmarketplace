package com.civileng.marketplace.analytics.warehouse;

import com.civileng.marketplace.analytics.cdc.TrackedTables.Fact;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Turns a captured row (column name → value, as the binlog or a snapshot read gives it) into a
 * warehouse fact. Upserts, so replaying a change — after a restart, a snapshot overlapping the
 * binlog, or the same rows copied to another cluster by a move — changes nothing.
 */
@RequiredArgsConstructor
public class Projector {

    private final JdbcTemplate jdbc;
    private final Pseudonyms pseudonyms;

    public void upsert(Fact fact, String tenantKey, Map<String, Object> row, String cluster) {
        Timestamp now = Timestamp.from(Instant.now());
        switch (fact) {
            case BOOKINGS -> jdbc.update("""
                    INSERT INTO fact_bookings (tenant_key, booking_id, status, service_category, city, payment_status,
                        total_amount, is_emergency, customer_token, created_at, updated_at, deleted_at, source_cluster, captured_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), service_category = VALUES(service_category),
                        city = VALUES(city), payment_status = VALUES(payment_status), total_amount = VALUES(total_amount),
                        is_emergency = VALUES(is_emergency), customer_token = VALUES(customer_token),
                        created_at = VALUES(created_at), updated_at = VALUES(updated_at), deleted_at = NULL,
                        source_cluster = VALUES(source_cluster), captured_at = VALUES(captured_at)""",
                    tenantKey, number(row.get("id")), text(row.get("status")), text(row.get("service_category")),
                    text(row.get("city")), text(row.get("payment_status")), decimal(row.get("total_amount")),
                    bool(row.get("is_emergency")), pseudonyms.of(tenantKey, row.get("customer_id")),
                    time(row.get("created_at")), time(row.get("updated_at")), cluster, now);
            case PAYMENTS -> jdbc.update("""
                    INSERT INTO fact_payments (tenant_key, payment_id, reference_type, status, total_amount, currency,
                        payer_token, paid_at, created_at, deleted_at, source_cluster, captured_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                    ON DUPLICATE KEY UPDATE reference_type = VALUES(reference_type), status = VALUES(status),
                        total_amount = VALUES(total_amount), currency = VALUES(currency), payer_token = VALUES(payer_token),
                        paid_at = VALUES(paid_at), created_at = VALUES(created_at), deleted_at = NULL,
                        source_cluster = VALUES(source_cluster), captured_at = VALUES(captured_at)""",
                    tenantKey, number(row.get("id")), text(row.get("reference_type")),
                    text(row.getOrDefault("payment_status", row.get("status"))), decimal(row.get("total_amount")),
                    text(row.get("currency")), pseudonyms.of(tenantKey, row.get("user_id")), time(row.get("paid_at")),
                    time(row.get("created_at")), cluster, now);
            case PURCHASE_ORDERS -> jdbc.update("""
                    INSERT INTO fact_purchase_orders (tenant_key, po_id, buyer_org_id, supplier_org_id, status, total,
                        created_at, deleted_at, source_cluster, captured_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                    ON DUPLICATE KEY UPDATE buyer_org_id = VALUES(buyer_org_id), supplier_org_id = VALUES(supplier_org_id),
                        status = VALUES(status), total = VALUES(total), created_at = VALUES(created_at), deleted_at = NULL,
                        source_cluster = VALUES(source_cluster), captured_at = VALUES(captured_at)""",
                    tenantKey, number(row.get("id")), number(row.get("buyer_org_id")), number(row.get("supplier_org_id")),
                    text(row.get("status")), decimal(row.get("total")), time(row.get("created_at")), cluster, now);
        }
    }

    /** A deleted source row stays in the warehouse, marked, so history does not silently change. */
    public void delete(Fact fact, String tenantKey, Map<String, Object> row) {
        String table = switch (fact) {
            case BOOKINGS -> "fact_bookings WHERE tenant_key = ? AND booking_id = ?";
            case PAYMENTS -> "fact_payments WHERE tenant_key = ? AND payment_id = ?";
            case PURCHASE_ORDERS -> "fact_purchase_orders WHERE tenant_key = ? AND po_id = ?";
        };
        String[] parts = table.split(" WHERE ", 2);
        jdbc.update("UPDATE " + parts[0] + " SET deleted_at = ? WHERE " + parts[1], Timestamp.from(Instant.now()),
                tenantKey, number(row.get("id")));
    }

    /** Everything of one tenant, from every fact table: a tenant's partition goes with the tenant. */
    public int purge(String tenantKey) {
        int n = 0;
        for (String t : new String[]{"fact_bookings", "fact_payments", "fact_purchase_orders"}) {
            n += jdbc.update("DELETE FROM " + t + " WHERE tenant_key = ?", tenantKey);
        }
        return n;
    }

    static String text(Object v) {
        if (v == null) return null;
        return v instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : v.toString();
    }

    static Long number(Object v) {
        if (v == null) return null;
        return v instanceof Number n ? n.longValue() : Long.valueOf(text(v));
    }

    static BigDecimal decimal(Object v) {
        if (v == null) return null;
        return v instanceof BigDecimal d ? d : new BigDecimal(text(v));
    }

    static Boolean bool(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return v instanceof Number n ? n.intValue() != 0 : Boolean.valueOf(text(v));
    }

    /**
     * Binlog temporal values arrive as epoch milliseconds (the capture reads DATETIME and TIMESTAMP
     * as UTC longs); snapshot reads as LocalDateTime/Timestamp. Stored as UTC wall time.
     */
    static Timestamp time(Object v) {
        if (v == null) return null;
        if (v instanceof Long ms) return Timestamp.valueOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC));
        if (v instanceof LocalDateTime t) return Timestamp.valueOf(t);
        if (v instanceof Timestamp t) return t;
        if (v instanceof java.util.Date d) return Timestamp.valueOf(LocalDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC));
        return Timestamp.valueOf(text(v).replace('T', ' '));
    }
}
