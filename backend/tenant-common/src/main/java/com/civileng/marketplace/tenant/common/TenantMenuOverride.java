package com.civileng.marketplace.tenant.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the operator has decided about one menu item for one tenant.
 *
 * <p>This replaces the older "hidden items" CSV. Hiding turned out to be the least interesting
 * thing an operator wants to say about a tenant's navigation: the same screen is where they want
 * to reorder the sidebar and rename a tab to the customer's own vocabulary — "Bookings" is
 * "Site Visits" to one tenant and "Inspections" to another. A key with a flag could not carry
 * either, so the flag became a row.
 *
 * <p>Every field but {@code itemKey} is an override, and null means "leave the catalogue's own
 * value alone". A row that overrides nothing and is visible is meaningless, and tenant-service
 * drops it rather than storing it — see {@code TenantService.setMenuOverrides}.
 *
 * <p>Note what this cannot do: it cannot show an item whose module the tenant does not have.
 * Modules decide what exists — they gate the API at the gateway as well as the menu — and this
 * only shapes what survives that. Making an item visible here that the module set has removed
 * would put a tab in the sidebar that 404s on click.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMenuOverride {

    /** Matches {@code ui_menu_items.item_key} in admin-service. */
    private String itemKey;

    /** False hides the item. Null and true both mean "show it", subject to the module set. */
    private Boolean visible;

    /** Replaces the catalogue label for this tenant. Null keeps the shipped wording. */
    private String labelOverride;

    /** Replaces the catalogue sort order. Null keeps the shipped position. */
    private Integer sortOrder;

    /**
     * The only roles that may see this item in this tenant, comma-separated. Null leaves the
     * catalogue's defaults. A ceiling, not a default: the tenant's own admins can hide the item from
     * one of these roles in their workspace menus, but cannot show it to a role outside them.
     */
    private String roles;

    /** {@link #roles} as a list; empty when the catalogue's defaults apply. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.List<String> roleList() {
        if (roles == null || roles.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    public boolean isHidden() {
        return Boolean.FALSE.equals(visible);
    }

    /** True when this row says nothing the catalogue does not already say. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNoop() {
        return !isHidden() && labelOverride == null && sortOrder == null && (roles == null || roles.isBlank());
    }
}
