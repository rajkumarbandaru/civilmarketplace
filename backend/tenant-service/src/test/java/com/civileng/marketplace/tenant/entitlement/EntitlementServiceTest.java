package com.civileng.marketplace.tenant.entitlement;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementServiceTest {

    private final EntitlementFixtures f = new EntitlementFixtures();
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private final EntitlementService service = new EntitlementService(f.plans, f.subscriptions, f.grants,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Test
    void aPlanGivesItsFeaturesPlusTheBaseAndItsLimits() {
        f.subscribe("acme", "starter", "");
        var e = service.of("acme");
        assertThat(e.features()).contains("bookings", "auth", "users", "payments").doesNotContain("projects", "tenantadmin");
        assertThat(e.limits()).containsEntry("bookings.monthly", 500L).containsEntry("staff.seats", 5L);
    }

    @Test
    void addOnsAddFeaturesAndCapacity() {
        f.subscribe("acme", "starter", "projects,bookings-5k");
        var e = service.of("acme");
        assertThat(e.features()).contains("projects");
        assertThat(e.limits()).containsEntry("bookings.monthly", 5500L);
    }

    @Test
    void anUnlimitedLimitStaysUnlimited() {
        f.subscribe("acme", "enterprise", "bookings-5k");
        assertThat(service.of("acme").limits()).doesNotContainKey("bookings.monthly");
    }

    @Test
    void onlyActiveGrantsCountAndALimitGrantRaisesNotLowers() {
        f.subscribe("acme", "starter", "");
        f.grantRows.add(TenantGrant.builder().id(1L).tenantKey("acme").feature("projects").expiresAt(NOW_LOCAL.plusDays(3)).reason("trial").build());
        f.grantRows.add(TenantGrant.builder().id(2L).tenantKey("acme").feature("landrecords").expiresAt(NOW_LOCAL.minusDays(1)).reason("old").build());
        f.grantRows.add(TenantGrant.builder().id(3L).tenantKey("acme").feature("bookings.monthly").limitValue(2000L).expiresAt(NOW_LOCAL.plusDays(3)).reason("peak").build());
        f.grantRows.add(TenantGrant.builder().id(4L).tenantKey("acme").feature("staff.seats").limitValue(1L).expiresAt(NOW_LOCAL.plusDays(3)).reason("odd").build());
        f.grantRows.add(TenantGrant.builder().id(5L).tenantKey("acme").feature("search").expiresAt(NOW_LOCAL.plusDays(3)).reason("x").revokedAt(NOW_LOCAL).build());
        var e = service.of("acme");
        assertThat(e.features()).contains("projects").doesNotContain("landrecords");
        assertThat(e.limits()).containsEntry("bookings.monthly", 2000L).containsEntry("staff.seats", 5L);
        assertThat(e.grants()).extracting(EntitlementService.GrantView::active).containsExactly(true, false, true, true, false);
    }

    @Test
    void whatRunsIsTheTenantsChoicesWithinItsEntitlement() {
        f.subscribe("acme", "starter", "");
        Set<String> chosen = Set.of("auth", "bookings", "projects", "reviews");
        assertThat(EntitlementService.running(chosen, service.of("acme"))).containsExactlyInAnyOrder("auth", "bookings", "reviews");
    }

    @Test
    void theOperatorIsEntitledToEverythingIncludingTenantAdministration() {
        var e = service.of("platform");
        assertThat(e.features()).contains("tenantadmin", "landrecords", "projects", "auth");
        assertThat(e.limits()).isEmpty();
    }

    @Test
    void theCatalogOffersTheLatestVersionOfEachPlan() {
        assertThat(service.catalog()).extracting(EntitlementService.PlanView::key)
                .containsExactlyInAnyOrder("starter", "professional", "enterprise");
        assertThat(service.hypothetical("acme", "professional", List.of()).features()).contains("projects");
    }
}
