package com.civileng.marketplace.web.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformRolesTest {

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_OWNER", "PLATFORM_ADMIN", "PLATFORM_SUPPORT"})
    void platformStaffMayViewOnTheOperatorTenant(String role) {
        assertThat(PlatformRoles.canView("platform", role)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_OWNER", "PLATFORM_ADMIN"})
    void ownersAndAdminsMayManage(String role) {
        assertThat(PlatformRoles.canManage("platform", role)).isTrue();
    }

    @Test
    void supportMayNotManage() {
        assertThat(PlatformRoles.canManage("platform", "PLATFORM_SUPPORT")).isFalse();
        assertThatThrownBy(() -> PlatformRoles.require("platform", "PLATFORM_SUPPORT", true, "Tenants"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> PlatformRoles.require("platform", "PLATFORM_SUPPORT", false, "Tenants"))
                .doesNotThrowAnyException();
    }

    @Test
    void aPlatformRoleOnACustomerTenantIsNothing() {
        // The roles table is per tenant; a stray PLATFORM_OWNER row in a customer tenant must not
        // reach the factory.
        assertThat(PlatformRoles.canView("civengmarket", "PLATFORM_OWNER")).isFalse();
        assertThat(PlatformRoles.canManage("civengmarket", "PLATFORM_OWNER")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_OWNER", "ADMIN", "SUPER_ADMIN", "CUSTOMER"})
    void tenantRolesAreNeverPlatformStaffEvenOnTheOperatorTenant(String role) {
        assertThat(PlatformRoles.canView("platform", role)).isFalse();
        assertThatThrownBy(() -> PlatformRoles.requireManage("platform", role, "The factory"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("platform owners and admins");
    }

    @Test
    void nullsAreRefused() {
        assertThat(PlatformRoles.canView(null, null)).isFalse();
        assertThat(PlatformRoles.isPlatformRole(null)).isFalse();
    }
}
