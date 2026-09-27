package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAssignmentPolicyTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_OWNER", "PLATFORM_ADMIN", "PLATFORM_SUPPORT"})
    void theRkConsoleHasPlatformRoles(String role) {
        assertThat(RoleAssignmentPolicy.assignableIn("platform", role)).isTrue();
        assertThat(RoleAssignmentPolicy.assignableIn("civengmarket", role)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_OWNER", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN",
            "CUSTOMER", "CIVIL_ENGINEER", "ARCHITECT", "MATERIAL_SUPPLIER", "SITE_MANAGER_CUSTOM"})
    void everyOtherRoleBelongsToATenantAndNeverToTheRkConsole(String role) {
        assertThat(RoleAssignmentPolicy.assignableIn("civengmarket", role)).isTrue();
        assertThat(RoleAssignmentPolicy.assignableIn("platform", role)).isFalse();
    }

    @Test
    void theRkConsolesRoleListIsOnlyPlatformRoles() {
        List<String> all = List.of("ARCHITECT", "CUSTOMER", "ADMIN", "TENANT_OWNER",
                "PLATFORM_ADMIN", "PLATFORM_OWNER", "PLATFORM_SUPPORT");
        assertThat(RoleAssignmentPolicy.visibleIn("platform", all))
                .containsExactly("PLATFORM_ADMIN", "PLATFORM_OWNER", "PLATFORM_SUPPORT");
        assertThat(RoleAssignmentPolicy.visibleIn("civengmarket", all))
                .containsExactly("ARCHITECT", "CUSTOMER", "ADMIN", "TENANT_OWNER");
    }

    @Test
    void aMemberRoleCannotBeGivenOnTheRkConsole() {
        TenantContext.set("platform");
        assertThatThrownBy(() -> RoleAssignmentPolicy.check("CUSTOMER", "PLATFORM_OWNER"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("only has platform roles");
        assertThatCode(() -> RoleAssignmentPolicy.check("PLATFORM_SUPPORT", "PLATFORM_ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    void onlyAnOwnerGivesAnOwnerRole() {
        TenantContext.set("platform");
        assertThatThrownBy(() -> RoleAssignmentPolicy.check("PLATFORM_OWNER", "PLATFORM_ADMIN"))
                .isInstanceOf(SecurityException.class);
        TenantContext.set("civengmarket");
        assertThatThrownBy(() -> RoleAssignmentPolicy.check("TENANT_OWNER", "ADMIN"))
                .isInstanceOf(SecurityException.class);
        assertThatCode(() -> RoleAssignmentPolicy.check("TENANT_OWNER", "TENANT_OWNER"))
                .doesNotThrowAnyException();
    }
}
