package com.civileng.marketplace.admin.uiconfig;

import com.civileng.marketplace.admin.client.AuthServiceClient;
import com.civileng.marketplace.admin.config.ConfigService;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.WorkspaceMenuRow;
import com.civileng.marketplace.admin.uiconfig.model.MenuItemDefinition;
import com.civileng.marketplace.admin.uiconfig.model.TenantMenuOverrideRow;
import com.civileng.marketplace.admin.uiconfig.model.WorkspaceMenuEntry;
import com.civileng.marketplace.admin.uiconfig.repository.CustomThemePresetRepository;
import com.civileng.marketplace.admin.uiconfig.repository.MenuItemDefinitionRepository;
import com.civileng.marketplace.admin.uiconfig.repository.TenantMenuOverrideRowRepository;
import com.civileng.marketplace.admin.uiconfig.repository.TenantModuleRepository;
import com.civileng.marketplace.admin.uiconfig.repository.TenantNavigationRepository;
import com.civileng.marketplace.admin.uiconfig.repository.UserAppearanceRepository;
import com.civileng.marketplace.admin.uiconfig.repository.UserMenuOverrideRepository;
import com.civileng.marketplace.admin.uiconfig.repository.WorkspaceMenuEntryRepository;
import com.civileng.marketplace.admin.uiconfig.service.RoleDirectory;
import com.civileng.marketplace.admin.uiconfig.service.UiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The platform's per-tenant role ceiling on a menu item: the roles it names see the item by default,
 * and no workspace overlay can show it to anyone else.
 */
class TenantMenuRoleCeilingTest {

    private final MenuItemDefinitionRepository items = mock(MenuItemDefinitionRepository.class);
    private final WorkspaceMenuEntryRepository workspaceMenus = mock(WorkspaceMenuEntryRepository.class);
    private final TenantMenuOverrideRowRepository overrides = mock(TenantMenuOverrideRowRepository.class);
    private final TenantModuleRepository modules = mock(TenantModuleRepository.class);
    private final RoleDirectory roles = mock(RoleDirectory.class);

    private final UiConfigService service = new UiConfigService(items, workspaceMenus,
            mock(UserMenuOverrideRepository.class), mock(ConfigService.class), modules, overrides,
            mock(TenantNavigationRepository.class), mock(CustomThemePresetRepository.class),
            mock(UserAppearanceRepository.class), roles, mock(AuthServiceClient.class));

    private static MenuItemDefinition item(String key, int order, String defaultRoles) {
        return MenuItemDefinition.builder().itemKey(key).label(key).path("/" + key).icon("Circle")
                .section("Main").sortOrder(order).defaultRoles(defaultRoles).build();
    }

    @BeforeEach
    void catalogue() {
        when(items.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                item("bookings", 10, "*"),
                item("procurement", 20, "*")));
        when(modules.findAll()).thenReturn(List.of());
        when(roles.exists(anyString())).thenReturn(true);
        when(workspaceMenus.findByRole(anyString())).thenReturn(List.of());

        TenantMenuOverrideRow procurement = new TenantMenuOverrideRow();
        procurement.setItemKey("procurement");
        procurement.setVisible(true);
        procurement.setRoles("MATERIAL_SUPPLIER,CIVIL_ENGINEER");
        when(overrides.findAll()).thenReturn(List.of(procurement));
    }

    private static List<String> keys(List<WorkspaceMenuRow> rows) {
        return rows.stream().map(WorkspaceMenuRow::itemKey).toList();
    }

    @Test
    void theNamedRolesSeeTheItem() {
        List<WorkspaceMenuRow> supplier = service.workspaceMenu("MATERIAL_SUPPLIER");
        assertThat(keys(supplier)).containsExactly("bookings", "procurement");
        assertThat(supplier.get(1).visible()).isTrue();
    }

    @Test
    void anyOtherRoleDoesNotEvenHaveItToSwitchOn() {
        assertThat(keys(service.workspaceMenu("CUSTOMER"))).containsExactly("bookings");
    }

    @Test
    void aWorkspaceOverlayCannotWidenTheCeiling() {
        WorkspaceMenuEntry showToWorkers = new WorkspaceMenuEntry("WORKER", "procurement");
        showToWorkers.setVisible(true);
        when(workspaceMenus.findByRole("WORKER")).thenReturn(List.of(showToWorkers));

        assertThat(keys(service.workspaceMenu("WORKER"))).containsExactly("bookings");
    }

    @Test
    void aWorkspaceOverlayCanStillNarrowIt() {
        WorkspaceMenuEntry hideFromEngineers = new WorkspaceMenuEntry("CIVIL_ENGINEER", "procurement");
        hideFromEngineers.setVisible(false);
        when(workspaceMenus.findByRole("CIVIL_ENGINEER")).thenReturn(List.of(hideFromEngineers));

        WorkspaceMenuRow row = service.workspaceMenu("CIVIL_ENGINEER").get(1);
        assertThat(row.itemKey()).isEqualTo("procurement");
        assertThat(row.visible()).isFalse();
    }

    @Test
    void theShippedCatalogueIsNeverChanged() {
        service.workspaceMenu("MATERIAL_SUPPLIER");
        MenuItemDefinition shipped = items.findAllByOrderBySortOrderAsc().get(1);
        assertThat(shipped.getDefaultRoles()).isEqualTo("*");
        assertThat(shipped.getRoleCeiling()).isNull();
    }
}
