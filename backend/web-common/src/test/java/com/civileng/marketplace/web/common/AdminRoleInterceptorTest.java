package com.civileng.marketplace.web.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminRoleInterceptorTest {

    private final AdminRoleInterceptor interceptor = new AdminRoleInterceptor();

    private static MockHttpServletRequest request(String method, String tenant, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/admin/users");
        if (tenant != null) request.addHeader("X-Tenant-Id", tenant);
        if (role != null) request.addHeader("X-User-Role", role);
        return request;
    }

    private boolean pass(String method, String tenant, String role) {
        return interceptor.preHandle(request(method, tenant, role), new MockHttpServletResponse(), new Object());
    }

    @Test
    void tenantStaffPassOnTheirTenant() {
        assertThat(pass("DELETE", "civengmarket", "TENANT_OWNER")).isTrue();
        assertThat(pass("POST", "civengmarket", "ADMIN")).isTrue();
    }

    @Test
    void membersAreRefused() {
        assertThatThrownBy(() -> pass("GET", "civengmarket", "CUSTOMER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void platformRolesOnlyCountOnTheOperatorTenant() {
        assertThat(pass("POST", "platform", "PLATFORM_OWNER")).isTrue();
        assertThatThrownBy(() -> pass("GET", "civengmarket", "PLATFORM_OWNER"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("platform console");
        assertThatThrownBy(() -> pass("GET", null, "PLATFORM_ADMIN"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void tenantStaffRolesAreRefusedOnTheOperatorTenant() {
        assertThatThrownBy(() -> pass("GET", "platform", "TENANT_OWNER"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("platform staff only");
        assertThatThrownBy(() -> pass("GET", "platform", "ADMIN"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void platformSupportIsReadOnly() {
        assertThat(pass("GET", "platform", "PLATFORM_SUPPORT")).isTrue();
        assertThatThrownBy(() -> pass("PUT", "platform", "PLATFORM_SUPPORT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("read-only");
    }

    private boolean passActing(String method, String tenant, String role, String actingFrom) {
        MockHttpServletRequest request = request(method, tenant, role);
        request.addHeader(ActingTenant.HEADER, actingFrom);
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void platformStaffActingFromThePlatformPassOnACustomerTenant() {
        assertThat(passActing("PUT", "civengmarket", "PLATFORM_OWNER", "platform")).isTrue();
        assertThat(passActing("DELETE", "civengmarket", "PLATFORM_ADMIN", "platform")).isTrue();
        assertThat(passActing("GET", "civengmarket", "PLATFORM_SUPPORT", "platform")).isTrue();
    }

    @Test
    void platformSupportStaysReadOnlyWhileActing() {
        assertThatThrownBy(() -> passActing("POST", "civengmarket", "PLATFORM_SUPPORT", "platform"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void actingFromAnythingButThePlatformCountsForNothing() {
        assertThatThrownBy(() -> passActing("GET", "civengmarket", "PLATFORM_OWNER", "acme"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("platform console");
    }

    @Test
    void aTenantRoleCannotClaimToBeActing() {
        assertThatThrownBy(() -> passActing("GET", "civengmarket", "TENANT_OWNER", "platform"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only platform staff");
    }
}
