package com.civileng.marketplace.admin.uiconfig.controller;

import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.admin.uiconfig.model.MenuItemDefinition;
import com.civileng.marketplace.admin.uiconfig.repository.MenuItemDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The full menu catalogue with each item's required module, for the operator console.
 *
 * <p>It exists so the Tenants screen can show what a tenant's sidebar will actually contain while
 * the operator is still ticking modules — the alternative is creating the tenant and then signing
 * in as them to find out, which the operator cannot do.
 *
 * <p>This is the catalogue in the *operator's* schema. That is the right list to preview from:
 * Flyway seeds the identical catalogue into every tenant, so the rows are the same everywhere, and
 * what differs per tenant — which of them survive — is exactly what the preview computes from the
 * module set.
 */
@RestController
@RequestMapping("/api/v1/admin/menu-catalogue")
@RequiredArgsConstructor
public class MenuCatalogueController {

    private final MenuItemDefinitionRepository menuItemRepository;

    /** @param requiredModule null for horizontal items every tenant has. */
    public record CatalogueEntry(String itemKey, String label, String path, String icon,
                                 String section, String menuGroup, int sortOrder,
                                 String requiredModule, String defaultRoles) {
    }

    @GetMapping
    @Operation(summary = "Every menu item and the module it needs (Super Admin)")
    public ResponseEntity<List<CatalogueEntry>> catalogue(
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        if (!"SUPER_ADMIN".equals(role)) {
            throw new AccessDeniedException("SUPER_ADMIN role required to read the menu catalogue");
        }

        List<CatalogueEntry> entries = menuItemRepository.findAllByOrderBySortOrderAsc().stream()
                .map(MenuCatalogueController::toEntry)
                .toList();
        return ResponseEntity.ok(entries);
    }

    private static CatalogueEntry toEntry(MenuItemDefinition item) {
        return new CatalogueEntry(
                item.getItemKey(), item.getLabel(), item.getPath(), item.getIcon(),
                item.getSection(), item.getMenuGroup(), item.getSortOrder(),
                item.getRequiredModule(), item.getDefaultRoles());
    }
}
