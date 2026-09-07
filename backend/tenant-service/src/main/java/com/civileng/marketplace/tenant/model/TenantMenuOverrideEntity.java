package com.civileng.marketplace.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * One operator decision about one menu item for one tenant.
 *
 * <p>Named …Entity to leave the plain name to {@code TenantMenuOverride} in tenant-common, which is
 * the wire shape three services share. The two are deliberately separate: this one carries a
 * composite key and a timestamp that mean nothing off the wire, and the shared class must stay free
 * of JPA so tenant-common can be a dependency of services that are not JPA-based at all.
 */
@Entity
@Table(name = "tenant_menu_override")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantMenuOverrideEntity {

    @EmbeddedId
    private Key id;

    /** False hides the item for this tenant. */
    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "label_override", length = 120)
    private String labelOverride;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public TenantMenuOverrideEntity(String tenantKey, String itemKey) {
        this.id = new Key(tenantKey, itemKey);
        this.visible = true;
    }

    public String itemKey() {
        return id == null ? null : id.getItemKey();
    }

    /** The shared wire shape, for the API response and the tenant.events message. */
    public com.civileng.marketplace.tenant.common.TenantMenuOverride toMessage() {
        return com.civileng.marketplace.tenant.common.TenantMenuOverride.builder()
                .itemKey(itemKey())
                .visible(visible)
                .labelOverride(labelOverride)
                .sortOrder(sortOrder)
                .build();
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "tenant_key", nullable = false, length = 31)
        private String tenantKey;

        @Column(name = "item_key", nullable = false, length = 64)
        private String itemKey;
    }
}
