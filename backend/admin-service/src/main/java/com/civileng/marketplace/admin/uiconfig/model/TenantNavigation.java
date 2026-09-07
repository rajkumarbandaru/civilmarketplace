package com.civileng.marketplace.admin.uiconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where this tenant's console opens, synced from {@code tenant.events}.
 *
 * <p>One row, keyed on a constant, so "at most one landing page" is a database guarantee. A missing
 * row means the shipped dashboard — which is also what a tenant whose sync has not arrived yet gets,
 * and the right answer in both cases.
 */
@Entity
@Table(name = "ui_tenant_navigation")
@Data
@NoArgsConstructor
public class TenantNavigation {

    public static final String SCOPE = "TENANT";

    @Id
    @Column(name = "scope_key", nullable = false, length = 20)
    private String scopeKey = SCOPE;

    @Column(name = "landing_path", length = 200)
    private String landingPath;

    public TenantNavigation(String landingPath) {
        this.scopeKey = SCOPE;
        this.landingPath = landingPath;
    }
}
