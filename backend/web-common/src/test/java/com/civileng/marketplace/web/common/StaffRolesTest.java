package com.civileng.marketplace.web.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffRolesTest {

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_OWNER", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN",
            "PLATFORM_OWNER", "PLATFORM_ADMIN", "PLATFORM_SUPPORT"})
    void staffRoles(String role) {
        assertThat(StaffRoles.isStaff(role)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WORKER", "SUPER_ADMIN"})
    void notStaff(String role) {
        // SUPER_ADMIN was split into TENANT_OWNER and PLATFORM_OWNER; the old name opens nothing.
        assertThat(StaffRoles.isStaff(role)).isFalse();
    }

    @Test
    void ownersAreTheTenantOwnerAndThePlatformOwner() {
        assertThat(StaffRoles.isOwner("TENANT_OWNER")).isTrue();
        assertThat(StaffRoles.isOwner("PLATFORM_OWNER")).isTrue();
        assertThat(StaffRoles.isOwner("ADMIN")).isFalse();
        assertThat(StaffRoles.isOwner("PLATFORM_ADMIN")).isFalse();
        assertThatThrownBy(() -> StaffRoles.requireOwner("ADMIN", "change the theme"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aPlatformAdminActingOnATenantIsItsOwner() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader(ActingTenant.HEADER, "platform");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            assertThat(StaffRoles.isOwner("PLATFORM_ADMIN")).isTrue();
            assertThat(StaffRoles.isOwner("PLATFORM_SUPPORT")).as("support only reads").isFalse();
            assertThat(StaffRoles.isOwner("ADMIN")).as("a tenant admin is still not the owner").isFalse();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
        assertThat(StaffRoles.isOwner("PLATFORM_ADMIN")).as("not acting: not an owner").isFalse();
    }

    @Test
    void theOwnerRoleDependsOnTheTenant() {
        assertThat(StaffRoles.ownerRoleFor("platform")).isEqualTo("PLATFORM_OWNER");
        assertThat(StaffRoles.ownerRoleFor("civengmarket")).isEqualTo("TENANT_OWNER");
    }

    @Test
    void managersIncludeAdminsButNotSupport() {
        assertThat(StaffRoles.isManager("ADMIN")).isTrue();
        assertThat(StaffRoles.isManager("PLATFORM_ADMIN")).isTrue();
        assertThat(StaffRoles.isManager("PLATFORM_SUPPORT")).isFalse();
        assertThat(StaffRoles.isManager("SUB_ADMIN")).isFalse();
    }
}
