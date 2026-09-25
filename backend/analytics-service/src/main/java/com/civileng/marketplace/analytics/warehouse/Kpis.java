package com.civileng.marketplace.analytics.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** What the warehouse answers. Every query is either for one tenant_key or explicitly across all. */
@Component
@RequiredArgsConstructor
public class Kpis {

    private final JdbcTemplate jdbc;

    public record TenantKpis(String tenantKey, long bookings, long bookingsLast30Days, long cancelledBookings,
                             long distinctCustomers, BigDecimal paymentsCompleted, long purchaseOrders,
                             BigDecimal purchaseOrderValue) { }

    public record Breakdown(String label, long count) { }

    public record Workspace(TenantKpis totals, List<Breakdown> bookingsByStatus, List<Breakdown> bookingsByCity) { }

    public record Platform(TenantKpis totals, List<TenantKpis> tenants) { }

    private static final String PER_TENANT = """
            SELECT t.tenant_key,
              (SELECT COUNT(*) FROM fact_bookings b WHERE b.tenant_key = t.tenant_key AND b.deleted_at IS NULL) AS bookings,
              (SELECT COUNT(*) FROM fact_bookings b WHERE b.tenant_key = t.tenant_key AND b.deleted_at IS NULL
                 AND b.created_at >= ?) AS recent,
              (SELECT COUNT(*) FROM fact_bookings b WHERE b.tenant_key = t.tenant_key AND b.status = 'CANCELLED') AS cancelled,
              (SELECT COUNT(DISTINCT customer_token) FROM fact_bookings b WHERE b.tenant_key = t.tenant_key) AS customers,
              (SELECT COALESCE(SUM(total_amount), 0) FROM fact_payments p WHERE p.tenant_key = t.tenant_key
                 AND p.status = 'COMPLETED') AS paid,
              (SELECT COUNT(*) FROM fact_purchase_orders o WHERE o.tenant_key = t.tenant_key AND o.status <> 'CANCELLED') AS pos,
              (SELECT COALESCE(SUM(total), 0) FROM fact_purchase_orders o WHERE o.tenant_key = t.tenant_key
                 AND o.status <> 'CANCELLED') AS po_value
            FROM (SELECT tenant_key FROM fact_bookings UNION SELECT tenant_key FROM fact_payments
                  UNION SELECT tenant_key FROM fact_purchase_orders) t
            """;

    public Workspace workspace(String tenantKey) {
        List<TenantKpis> one = query(PER_TENANT + " WHERE t.tenant_key = ?", since30Days(), tenantKey);
        TenantKpis totals = one.isEmpty() ? empty(tenantKey) : one.get(0);
        return new Workspace(totals,
                breakdown("SELECT status, COUNT(*) FROM fact_bookings WHERE tenant_key = ? AND deleted_at IS NULL GROUP BY status ORDER BY 2 DESC", tenantKey),
                breakdown("SELECT city, COUNT(*) FROM fact_bookings WHERE tenant_key = ? AND deleted_at IS NULL GROUP BY city ORDER BY 2 DESC LIMIT 10", tenantKey));
    }

    public Platform platform() {
        List<TenantKpis> tenants = query(PER_TENANT + " ORDER BY bookings DESC, tenant_key", since30Days());
        TenantKpis total = new TenantKpis("*",
                tenants.stream().mapToLong(TenantKpis::bookings).sum(),
                tenants.stream().mapToLong(TenantKpis::bookingsLast30Days).sum(),
                tenants.stream().mapToLong(TenantKpis::cancelledBookings).sum(),
                tenants.stream().mapToLong(TenantKpis::distinctCustomers).sum(),
                tenants.stream().map(TenantKpis::paymentsCompleted).reduce(BigDecimal.ZERO, BigDecimal::add),
                tenants.stream().mapToLong(TenantKpis::purchaseOrders).sum(),
                tenants.stream().map(TenantKpis::purchaseOrderValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Platform(total, tenants);
    }

    private List<TenantKpis> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> new TenantKpis(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                rs.getLong(5), rs.getBigDecimal(6), rs.getLong(7), rs.getBigDecimal(8)), args);
    }

    private List<Breakdown> breakdown(String sql, String tenantKey) {
        return jdbc.query(sql, (rs, i) -> new Breakdown(rs.getString(1) == null ? "—" : rs.getString(1), rs.getLong(2)), tenantKey);
    }

    private static java.sql.Timestamp since30Days() {
        return java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(30)));
    }

    private static TenantKpis empty(String tenantKey) {
        return new TenantKpis(tenantKey, 0, 0, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
    }
}
