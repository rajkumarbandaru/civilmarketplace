package com.civileng.marketplace.analytics;

import com.civileng.marketplace.analytics.cdc.TrackedTables;
import com.civileng.marketplace.analytics.cdc.TrackedTables.Fact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedTablesTest {

    @Test
    void theTenantIsTheSchemaSuffixOfATrackedTable() {
        assertThat(TrackedTables.match("civil_engineer_bookings_acme", "bookings"))
                .hasValueSatisfying(m -> {
                    assertThat(m.tenantKey()).isEqualTo("acme");
                    assertThat(m.fact()).isEqualTo(Fact.BOOKINGS);
                });
        assertThat(TrackedTables.match("civil_engineer_payments_platform", "payments")).get()
                .extracting(TrackedTables.Match::fact).isEqualTo(Fact.PAYMENTS);
        assertThat(TrackedTables.match("civil_engineer_procurement_b2b1", "purchase_orders")).isPresent();
    }

    @Test
    void everythingElseIsIgnored() {
        assertThat(TrackedTables.match("civil_engineer_bookings_acme", "service_categories")).isEmpty();
        assertThat(TrackedTables.match("civil_engineer_bookings_acme__r120501", "bookings")).as("restore side schema").isEmpty();
        assertThat(TrackedTables.match("civil_engineer_bookings_acme__pre120501", "bookings")).isEmpty();
        assertThat(TrackedTables.match("civil_engineer_bookings", "bookings")).as("legacy schema").isEmpty();
        assertThat(TrackedTables.match("civil_engineer_users_acme", "users")).isEmpty();
        assertThat(TrackedTables.match("civil_engineer_warehouse", "fact_bookings")).isEmpty();
        assertThat(TrackedTables.match(null, "bookings")).isEmpty();
    }
}
