package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.tenant.common.TenantMenuOverride;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The role ceiling the platform sets on a tenant's menu item, as tenant-service stores it. */
class MenuOverrideRolesTest {

    @Test
    void rolesAreStoredUpperCaseOnceEachInOrder() {
        assertThat(TenantService.normaliseRoles("procurement", List.of(" material_supplier", "CIVIL_ENGINEER", "Material_Supplier")))
                .isEqualTo("MATERIAL_SUPPLIER,CIVIL_ENGINEER");
    }

    @Test
    void noRolesMeansTheCataloguesDefaults() {
        assertThat(TenantService.normaliseRoles("procurement", List.of())).isNull();
        assertThat(TenantService.normaliseRoles("procurement", null)).isNull();
    }

    @Test
    void platformRolesAndJunkAreRefused() {
        assertThatThrownBy(() -> TenantService.normaliseRoles("procurement", List.of("PLATFORM_OWNER")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Platform roles");
        assertThatThrownBy(() -> TenantService.normaliseRoles("procurement", List.of("ADMIN; DROP TABLE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRowThatOnlyRestrictsRolesIsNotANoop() {
        TenantMenuOverride onlyRoles = TenantMenuOverride.builder().itemKey("procurement").roles("CUSTOMER").build();
        assertThat(onlyRoles.isNoop()).isFalse();
        assertThat(onlyRoles.roleList()).containsExactly("CUSTOMER");
        assertThat(TenantMenuOverride.builder().itemKey("x").roles(" ").build().isNoop()).isTrue();
    }
}
