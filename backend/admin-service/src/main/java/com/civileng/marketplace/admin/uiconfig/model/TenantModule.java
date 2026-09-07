package com.civileng.marketplace.admin.uiconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One module this tenant has, synced from {@code tenant.events}.
 *
 * <p>A copy rather than a lookup: the menu is filtered inside the tenant's own schema, and there is
 * nothing to ask at that point — the authoritative set lives in tenant-service, on the other side
 * of a request that has already been routed.
 */
@Entity
@Table(name = "ui_tenant_module")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantModule {

    @Id
    @Column(name = "module_key", nullable = false, length = 40)
    private String moduleKey;
}
