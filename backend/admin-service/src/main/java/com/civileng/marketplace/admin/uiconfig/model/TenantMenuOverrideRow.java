package com.civileng.marketplace.admin.uiconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One operator decision about one menu item for this tenant, synced from {@code tenant.events}.
 *
 * <p>Replaces {@code TenantHiddenMenuItem}, which could only say "not this one". Named …Row to keep
 * it distinct from {@code TenantMenuOverride} in tenant-common, the wire shape it is built from.
 *
 * <p>No tenant column: every table in this schema belongs to one tenant already, which is what
 * schema-per-tenant buys. The tenant key on the wire shape is how tenant-service addresses its own
 * rows, and it is spent by the time the event reaches here.
 */
@Entity
@Table(name = "ui_tenant_menu_override")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantMenuOverrideRow {

    @Id
    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    /** False hides the item for this tenant. */
    @Column(nullable = false)
    private boolean visible = true;

    /** Replaces the catalogue label. Null keeps the shipped wording. */
    @Column(name = "label_override", length = 120)
    private String labelOverride;

    /** Replaces the catalogue position. Null keeps the shipped order. */
    @Column(name = "sort_order")
    private Integer sortOrder;

    public static TenantMenuOverrideRow from(
            com.civileng.marketplace.tenant.common.TenantMenuOverride message) {
        TenantMenuOverrideRow row = new TenantMenuOverrideRow();
        row.itemKey = message.getItemKey();
        row.visible = !message.isHidden();
        row.labelOverride = message.getLabelOverride();
        row.sortOrder = message.getSortOrder();
        return row;
    }
}
