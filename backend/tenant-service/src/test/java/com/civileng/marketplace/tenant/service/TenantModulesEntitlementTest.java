package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.tenant.entitlement.EntitlementService;
import com.civileng.marketplace.tenant.entitlement.TenantSubscriptionRepository;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.repository.TenantMenuOverrideRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantModulesEntitlementTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    @SuppressWarnings("unchecked")
    private final TenantService service = new TenantService(tenants, mock(TenantMenuOverrideRepository.class),
            mock(KafkaTemplate.class), mock(TenantIntegrationService.class), mock(TenantLifecycle.class),
            entitlements, mock(TenantSubscriptionRepository.class), mock(com.civileng.marketplace.tenant.domain.DomainService.class),
            new com.civileng.marketplace.tenant.domain.DomainProperties(null, null, null, null, null, null, null, null, null, null));

    private final Tenant acme = Tenant.builder().tenantKey("acme").status(TenantStatus.ACTIVE)
            .enabledModules("auth,bookings,projects").build();

    {
        when(tenants.findByTenantKey("acme")).thenReturn(Optional.of(acme));
        // Starter: no projects (the tenant chose it back when it was on a bigger plan).
        when(entitlements.of("acme")).thenReturn(new EntitlementService.Entitlements("acme", "starter", 1, "Starter",
                "ACTIVE", List.of(), new TreeSet<>(Set.of("auth", "users", "bookings", "reviews")), Map.of(), List.of()));
    }

    @Test
    void cannotSwitchOnWhatThePlanDoesNotInclude() {
        assertThatThrownBy(() -> service.setModules("acme", Set.of("auth", "bookings", "projects", "landrecords"), "1"))
                .hasMessageContaining("Not in this tenant's plan: landrecords");
    }

    @Test
    void keepsADormantChoiceAndAcceptsEntitledOnes() {
        // setModules announces after commit, so it needs the synchronization a transaction provides.
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.setModules("acme", Set.of("auth", "bookings", "projects", "reviews"), "1");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(acme.moduleKeys()).contains("projects", "reviews");
    }

    @org.junit.jupiter.api.Test
    void onlyPlatformHostsResolveBySubdomain() {
        org.assertj.core.api.Assertions.assertThat(service.platformSubdomain("acme.localhost")).contains("acme");
        org.assertj.core.api.Assertions.assertThat(service.platformSubdomain("acme.civilengineer.com")).contains("acme");
        org.assertj.core.api.Assertions.assertThat(service.platformSubdomain("acme.evil.example")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(service.platformSubdomain("x.acme.localhost")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(service.platformSubdomain("www.acme-builders.test")).isEmpty();
    }
}
