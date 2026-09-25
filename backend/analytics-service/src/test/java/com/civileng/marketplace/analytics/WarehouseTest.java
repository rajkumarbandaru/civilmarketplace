package com.civileng.marketplace.analytics;

import com.civileng.marketplace.analytics.cdc.CdcProperties;
import com.civileng.marketplace.analytics.cdc.ClusterCapture;
import com.civileng.marketplace.analytics.cdc.TrackedTables.Fact;
import com.civileng.marketplace.analytics.warehouse.Kpis;
import com.civileng.marketplace.analytics.warehouse.Projector;
import com.civileng.marketplace.analytics.warehouse.Pseudonyms;
import com.github.shyiko.mysql.binlog.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** The projection and the KPIs against H2 in MySQL mode (the partitioning clause aside, the same tables). */
class WarehouseTest {

    private JdbcTemplate jdbc;
    private Projector projector;
    private Kpis kpis;
    private final Pseudonyms pseudonyms = new Pseudonyms(Base64.getEncoder().encodeToString(new byte[32]));

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:w" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE fact_bookings (tenant_key VARCHAR(31) NOT NULL, booking_id BIGINT NOT NULL, status VARCHAR(30),
                  service_category VARCHAR(100), city VARCHAR(100), payment_status VARCHAR(30), total_amount DECIMAL(12,2),
                  is_emergency BOOLEAN, customer_token CHAR(16), created_at TIMESTAMP(3), updated_at TIMESTAMP(3),
                  deleted_at TIMESTAMP(3), source_cluster VARCHAR(40) NOT NULL, captured_at TIMESTAMP(3) NOT NULL,
                  PRIMARY KEY (tenant_key, booking_id))""");
        jdbc.execute("""
                CREATE TABLE fact_payments (tenant_key VARCHAR(31) NOT NULL, payment_id BIGINT NOT NULL, reference_type VARCHAR(30),
                  status VARCHAR(30), total_amount DECIMAL(12,2), currency VARCHAR(10), payer_token CHAR(16), paid_at TIMESTAMP(3),
                  created_at TIMESTAMP(3), deleted_at TIMESTAMP(3), source_cluster VARCHAR(40) NOT NULL,
                  captured_at TIMESTAMP(3) NOT NULL, PRIMARY KEY (tenant_key, payment_id))""");
        jdbc.execute("""
                CREATE TABLE fact_purchase_orders (tenant_key VARCHAR(31) NOT NULL, po_id BIGINT NOT NULL, buyer_org_id BIGINT,
                  supplier_org_id BIGINT, status VARCHAR(30), total DECIMAL(15,2), created_at TIMESTAMP(3), deleted_at TIMESTAMP(3),
                  source_cluster VARCHAR(40) NOT NULL, captured_at TIMESTAMP(3) NOT NULL, PRIMARY KEY (tenant_key, po_id))""");
        jdbc.execute("""
                CREATE TABLE cdc_offsets (cluster_id VARCHAR(40) PRIMARY KEY, binlog_file VARCHAR(100), binlog_pos BIGINT,
                  events BIGINT, last_event_at TIMESTAMP(3), updated_at TIMESTAMP(3))""");
        projector = new Projector(jdbc, pseudonyms);
        kpis = new Kpis(jdbc);
    }

    private static Map<String, Object> booking(long id, String status, String city, long customer) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("status", status.getBytes());          // the binlog gives strings as bytes
        r.put("service_category", "Plumbing".getBytes());
        r.put("city", city.getBytes());
        r.put("payment_status", "PENDING".getBytes());
        r.put("total_amount", new BigDecimal("1500.00"));
        r.put("is_emergency", 0);
        r.put("customer_id", customer);
        r.put("created_at", Instant.now().toEpochMilli());
        r.put("updated_at", Instant.now().toEpochMilli());
        return r;
    }

    @Test
    void capturedRowsBecomeFactsIdempotentlyAndWithoutRawCustomerIds() {
        projector.upsert(Fact.BOOKINGS, "acme", booking(1, "PENDING", "Pune", 7), "cluster-a");
        projector.upsert(Fact.BOOKINGS, "acme", booking(1, "PENDING", "Pune", 7), "cluster-a");   // replayed
        projector.upsert(Fact.BOOKINGS, "acme", booking(1, "CONFIRMED", "Pune", 7), "cluster-b");  // moved, then updated
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM fact_bookings WHERE tenant_key='acme' AND booking_id=1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fact_bookings", Long.class)).isEqualTo(1);
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(row.get("source_cluster")).isEqualTo("cluster-b");
        assertThat((String) row.get("customer_token")).hasSize(16).isNotEqualTo("7")
                .isEqualTo(pseudonyms.of("acme", 7L)).isNotEqualTo(pseudonyms.of("other", 7L));
    }

    @Test
    void deletesAreMarkedAndATenantsPartitionCanBePurged() {
        projector.upsert(Fact.BOOKINGS, "acme", booking(1, "PENDING", "Pune", 7), "cluster-a");
        projector.upsert(Fact.BOOKINGS, "bigco", booking(1, "PENDING", "Delhi", 9), "cluster-a");
        projector.delete(Fact.BOOKINGS, "acme", Map.of("id", 1L));
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM fact_bookings WHERE tenant_key='acme'", Object.class)).isNotNull();
        assertThat(projector.purge("acme")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT tenant_key FROM fact_bookings", String.class)).containsExactly("bigco");
    }

    @Test
    void aWorkspaceSeesOnlyItselfAndThePlatformSeesEveryTenant() {
        projector.upsert(Fact.BOOKINGS, "acme", booking(1, "PENDING", "Pune", 7), "cluster-a");
        projector.upsert(Fact.BOOKINGS, "acme", booking(2, "CANCELLED", "Pune", 7), "cluster-a");
        projector.upsert(Fact.BOOKINGS, "acme", booking(3, "PENDING", "Mumbai", 8), "cluster-a");
        projector.upsert(Fact.BOOKINGS, "bigco", booking(1, "PENDING", "Delhi", 9), "cluster-b");
        Map<String, Object> pay = new HashMap<>(Map.of("id", 5L, "payment_status", "COMPLETED".getBytes(), "total_amount",
                new BigDecimal("2500.00"), "user_id", 7L, "reference_type", "BOOKING".getBytes()));
        projector.upsert(Fact.PAYMENTS, "acme", pay, "cluster-a");
        projector.upsert(Fact.PURCHASE_ORDERS, "acme", new HashMap<>(Map.of("id", 9L, "status", "ISSUED".getBytes(),
                "total", new BigDecimal("403840.00"), "buyer_org_id", 1L, "supplier_org_id", 2L)), "cluster-a");

        Kpis.Workspace acme = kpis.workspace("acme");
        assertThat(acme.totals().bookings()).isEqualTo(3);
        assertThat(acme.totals().cancelledBookings()).isEqualTo(1);
        assertThat(acme.totals().distinctCustomers()).isEqualTo(2);
        assertThat(acme.totals().paymentsCompleted()).isEqualByComparingTo("2500.00");
        assertThat(acme.totals().purchaseOrderValue()).isEqualByComparingTo("403840.00");
        assertThat(acme.bookingsByCity()).extracting(Kpis.Breakdown::label).containsExactly("Pune", "Mumbai");
        assertThat(acme.bookingsByCity()).noneMatch(b -> b.label().equals("Delhi"));

        Kpis.Platform platform = kpis.platform();
        assertThat(platform.tenants()).extracting(Kpis.TenantKpis::tenantKey).containsExactly("acme", "bigco");
        assertThat(platform.totals().bookings()).isEqualTo(4);
        assertThat(kpis.workspace("nobody").totals().bookings()).isZero();
    }

    @Test
    void binlogRowEventsAreRoutedByTableAndMappedByColumnPosition() {
        List<Object[]> seen = new ArrayList<>();
        Projector recording = new Projector(jdbc, pseudonyms) {
            @Override
            public void upsert(Fact fact, String tenantKey, Map<String, Object> row, String cluster) {
                seen.add(new Object[]{fact, tenantKey, row.get("status"), cluster});
            }
        };
        ClusterCapture capture = new ClusterCapture("cluster-a", "mysql", 3306, 5400,
                new CdcProperties(true, "civil_cdc", "x", 5400), recording, jdbc) {
            @Override
            protected List<String> columnNames(String schema, String table) {
                return List.of("id", "status", "city");
            }
        };
        TableMapEventData map = new TableMapEventData();
        map.setTableId(42);
        map.setDatabase("civil_engineer_bookings_acme");
        map.setTable("bookings");
        capture.onEvent(event(EventType.TABLE_MAP, map));
        WriteRowsEventData write = new WriteRowsEventData();
        write.setTableId(42);
        write.setRows(List.<Serializable[]>of(new Serializable[]{1L, "PENDING".getBytes(), "Pune".getBytes()}));
        capture.onEvent(event(EventType.EXT_WRITE_ROWS, write));

        TableMapEventData other = new TableMapEventData();   // an untracked table is ignored
        other.setTableId(43);
        other.setDatabase("civil_engineer_users_acme");
        other.setTable("user_profiles");
        capture.onEvent(event(EventType.TABLE_MAP, other));
        WriteRowsEventData ignored = new WriteRowsEventData();
        ignored.setTableId(43);
        ignored.setRows(List.<Serializable[]>of(new Serializable[]{2L}));
        capture.onEvent(event(EventType.EXT_WRITE_ROWS, ignored));

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)[0]).isEqualTo(Fact.BOOKINGS);
        assertThat(seen.get(0)[1]).isEqualTo("acme");
        assertThat(new String((byte[]) seen.get(0)[2])).isEqualTo("PENDING");
        assertThat(capture.status().events()).isEqualTo(1);
    }

    private static Event event(EventType type, EventData data) {
        EventHeaderV4 h = new EventHeaderV4();
        h.setEventType(type);
        return new Event(h, data);
    }
}
