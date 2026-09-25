package com.civileng.marketplace.tenant.controller;

import com.civileng.marketplace.tenant.entitlement.EntitlementService;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.service.TenantIntegrationService;
import com.civileng.marketplace.tenant.service.TenantService;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceSettingsControllerTest {

    private final TenantService tenantService = mock(TenantService.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final TenantIntegrationService integrations = mock(TenantIntegrationService.class);
    private final WorkspaceSettingsController controller =
            new WorkspaceSettingsController(tenantService, entitlements, integrations);

    private final Tenant acme = Tenant.builder().tenantKey("acme").status(TenantStatus.ACTIVE)
            .enabledModules("auth,users,admin,audit,payments,bookings,procurement").build();

    @BeforeEach
    void setUp() {
        when(tenantService.byKey("acme")).thenReturn(acme);
        when(entitlements.of("acme")).thenReturn(new EntitlementService.Entitlements("acme", "professional", 1,
                "Professional", "ACTIVE", List.of(),
                new TreeSet<>(Set.of("auth", "users", "admin", "audit", "payments", "bookings", "projects", "reviews")),
                Map.of(), List.of()));
    }

    @Test
    void showsEveryModuleWithWhetherItIsChosenRunningEntitledAndLocked() {
        var view = controller.modules("ADMIN", "acme").getBody();

        assertThat(view.planName()).isEqualTo("Professional");
        assertThat(view.modules()).extracting(WorkspaceSettingsController.ModuleView::key)
                .doesNotContain("tenantadmin").contains("bookings", "projects", "procurement");
        var procurement = module(view, "procurement");
        assertThat(procurement.chosen()).isTrue();
        assertThat(procurement.entitled()).isFalse();
        assertThat(procurement.running()).isFalse();
        var projects = module(view, "projects");
        assertThat(projects.chosen()).isFalse();
        assertThat(projects.entitled()).isTrue();
        assertThat(module(view, "auth").locked()).isTrue();
        assertThat(module(view, "bookings").running()).isTrue();
    }

    @Test
    void savingModulesAlwaysKeepsTheLockedOnes() {
        when(tenantService.setModules(eq("acme"), any(), eq("9"))).thenReturn(acme);

        controller.setModules(new WorkspaceSettingsController.ModulesRequest(Set.of("bookings", "projects")),
                "9", "ADMIN", "acme");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> sent = ArgumentCaptor.forClass(Set.class);
        verify(tenantService).setModules(eq("acme"), sent.capture(), eq("9"));
        assertThat(sent.getValue()).containsExactlyInAnyOrder("auth", "users", "admin", "audit", "bookings", "projects");
    }

    @Test
    void tenantAdministrationCannotBeSwitchedOnByAWorkspace() {
        assertThatThrownBy(() -> controller.setModules(
                new WorkspaceSettingsController.ModulesRequest(Set.of("tenantadmin")), "9", "ADMIN", "acme"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyTheWorkspacesAdminsGetIn() {
        assertThatThrownBy(() -> controller.modules("CUSTOMER", "acme")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.integrations("ADMIN", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.integrations("SUPER_ADMIN", "platform"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void integrationsAreAlwaysTheCallersOwnTenant() {
        controller.integrations("ADMIN", "acme");
        verify(integrations).list("acme");
    }

    @Test
    void resettingAnIntegrationThatIsAlreadyOnThePlatformIsFine() {
        doThrow(new NoSuchElementException("none")).when(integrations).delete(eq("acme"), eq("sms"), anyString());

        assertThat(controller.resetIntegration("sms", "9", "ADMIN", "acme").getStatusCode().value()).isEqualTo(204);
    }

    private static WorkspaceSettingsController.ModuleView module(WorkspaceSettingsController.ModulesView view, String key) {
        return view.modules().stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow();
    }
}
