package com.civileng.marketplace.tenant.controller;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.model.Vertical;
import com.civileng.marketplace.tenant.service.TenantService;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantResolutionControllerTest {

    private final TenantService service = mock(TenantService.class);
    private final com.civileng.marketplace.tenant.entitlement.EntitlementService entitlements =
            mock(com.civileng.marketplace.tenant.entitlement.EntitlementService.class);
    private final TenantResolutionController controller = new TenantResolutionController(service, entitlements);

    @Test
    void currentReturnsPublicIdentityAndBrandingOnly() {
        Tenant acme = Tenant.builder().tenantKey("acme").name("Acme Builders").status(TenantStatus.ACTIVE)
                .vertical(Vertical.CIVIL_MARKETPLACE).enabledModules("bookings").contactEmail("ops@acme.in")
                .plan("GOLD").brandName("Acme").logoUrl("https://cdn/acme.png").primaryColor("#123456").build();
        when(service.byKey("acme")).thenReturn(acme);
        when(entitlements.runningModules(acme)).thenReturn(java.util.Set.of("auth", "bookings"));

        var body = controller.current("acme").getBody();

        assertThat(body.getName()).isEqualTo("Acme Builders");
        assertThat(body.getBranding().getLogoUrl()).isEqualTo("https://cdn/acme.png");
        assertThat(body.getContactEmail()).as("not public").isNull();
        assertThat(body.getPlan()).as("not public").isNull();
        assertThat(body.getModules()).as("what runs, not what was chosen").containsExactlyInAnyOrder("auth", "bookings");
    }

    @Test
    void aRequestWithNoResolvedTenantIsNotFound() {
        assertThatThrownBy(() -> controller.current(null)).isInstanceOf(NoSuchElementException.class);
    }
}
