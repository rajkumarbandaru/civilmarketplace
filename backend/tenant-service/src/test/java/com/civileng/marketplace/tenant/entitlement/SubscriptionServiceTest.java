package com.civileng.marketplace.tenant.entitlement;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.repository.TenantStatusChangeRepository;
import com.civileng.marketplace.tenant.service.TenantLifecycle;
import com.civileng.marketplace.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    private final EntitlementFixtures f = new EntitlementFixtures();
    private final TenantService tenantService = mock(TenantService.class);
    private Instant now = Instant.parse("2026-09-24T10:00:00Z");
    private SubscriptionService service;
    private Tenant acme;

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(inv -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        EntitlementService entitlements = new EntitlementService(f.plans, f.subscriptions, f.grants, clock);
        service = new SubscriptionService(entitlements, f.subscriptions, f.grants, tenantService,
                new TenantLifecycle(mock(TenantStatusChangeRepository.class)), clock);
        acme = Tenant.builder().tenantKey("acme").status(TenantStatus.ACTIVE).enabledModules("auth,bookings,projects,reviews").build();
        when(tenantService.byKey("acme")).thenReturn(acme);
        f.subscribe("acme", "professional", "");
    }

    private LocalDateTime local() {
        return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }

    @Test
    void previewOfADowngradeNamesWhatStopsAndTheLimitsThatShrink() {
        var impact = service.preview("acme", "starter", List.of());
        assertThat(impact.modulesStopping()).containsExactly("projects");
        assertThat(impact.modulesResuming()).isEmpty();
        assertThat(impact.limitChanges().get("bookings.monthly")).containsExactly(5000L, 500L);
    }

    @Test
    void downgradeThenUpgradeRestoresTheTenantsOwnChoices() {
        service.change("acme", "starter", List.of(), "1");
        assertThat(EntitlementService.running(acme.moduleKeys(), service.preview("acme", "starter", List.of()) == null ? null
                : new EntitlementService(f.plans, f.subscriptions, f.grants, Clock.systemUTC()).of("acme"))).doesNotContain("projects");
        assertThat(acme.moduleKeys()).as("the choice is kept").contains("projects");
        verify(tenantService).announce(acme, false);

        var impact = service.preview("acme", "professional", List.of());
        assertThat(impact.modulesResuming()).containsExactly("projects");
        service.change("acme", "professional", List.of(), "1");
        assertThat(f.subRows.get("acme").getPlanKey()).isEqualTo("professional");
    }

    @Test
    void anAddOnCanRestoreAModuleWithoutAnUpgrade() {
        service.change("acme", "starter", List.of("projects"), "1");
        assertThat(service.preview("acme", "starter", List.of()).modulesStopping()).containsExactly("projects");
        assertThatThrownBy(() -> service.change("acme", "starter", List.of("nonsense"), "1")).hasMessageContaining("Unknown add-on");
    }

    @Test
    void grantsMustExpireWithinAYearAndHaveAReason() {
        assertThatThrownBy(() -> service.grant("acme", "landrecords", null, local().plusMonths(13), "trial", "1"))
                .hasMessageContaining("at most 12 months");
        assertThatThrownBy(() -> service.grant("acme", "landrecords", null, local().minusDays(1), "trial", "1"))
                .hasMessageContaining("future");
        assertThatThrownBy(() -> service.grant("acme", "landrecords", null, local().plusDays(10), " ", "1"))
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> service.grant("acme", "tenantadmin", null, local().plusDays(10), "x", "1"))
                .hasMessageContaining("cannot be granted");
        assertThatThrownBy(() -> service.grant("acme", "auth", null, local().plusDays(10), "x", "1"))
                .as("base modules need no grant").hasMessageContaining("cannot be granted");
        assertThatThrownBy(() -> service.grant("acme", "bookings.monthly", null, local().plusDays(10), "x", "1"))
                .hasMessageContaining("needs a value");

        var e = service.grant("acme", "landrecords", null, local().plusDays(10), "Trial for Q4", "1");
        assertThat(e.features()).contains("landrecords");
        verify(tenantService).announce(acme, false);
    }

    @Test
    void revokingAGrantTakesItAwayAndAnExpiredOneIsAnnouncedOnce() {
        service.grant("acme", "landrecords", null, local().plusDays(10), "Trial", "1");
        assertThat(service.revokeGrant("acme", 1L, "1").features()).doesNotContain("landrecords");

        service.grant("acme", "landrecords", null, local().plusMinutes(5), "Short trial", "1");
        when(f.grants.findByExpiresAtBeforeAndExpiryAnnouncedFalseAndRevokedAtIsNull(any())).thenAnswer(inv -> f.grantRows.stream()
                .filter(g -> g.getRevokedAt() == null && !g.isExpiryAnnounced() && g.getExpiresAt().isBefore(inv.getArgument(0))).toList());
        now = now.plus(Duration.ofMinutes(10));
        clearInvocations(tenantService);
        service.announceExpiredGrants();
        service.announceExpiredGrants();
        verify(tenantService, times(1)).announce(acme, false);
    }

    @Test
    void aSuspendedSubscriptionSuspendsTheTenant() {
        service.setStatus("acme", TenantSubscription.Status.PAST_DUE, "billing");
        assertThat(acme.getStatus()).as("past due only warns").isEqualTo(TenantStatus.ACTIVE);
        service.setStatus("acme", TenantSubscription.Status.SUSPENDED, "billing");
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void theOperatorHasNoPlanToChange() {
        when(tenantService.byKey("platform")).thenReturn(Tenant.builder().tenantKey("platform").build());
        assertThatThrownBy(() -> service.change("platform", "starter", List.of(), "1")).hasMessageContaining("operator");
    }
}
