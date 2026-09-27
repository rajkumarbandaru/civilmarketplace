package com.civileng.marketplace.notification.controller;

import com.civileng.marketplace.notification.service.EmailService;
import com.civileng.marketplace.notification.service.EmailTemplateService;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who may read and who may change the email templates. Any staff member reads them; only the
 * workspace's owner — TENANT_OWNER, or PLATFORM_OWNER on the operator tenant — changes them. The
 * retired SUPER_ADMIN role does neither.
 */
class AdminEmailTemplateControllerTest {

    private final EmailTemplateService templates = mock(EmailTemplateService.class);
    private final EmailService email = mock(EmailService.class);
    private final AdminEmailTemplateController controller = new AdminEmailTemplateController(templates, email);

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_OWNER", "ADMIN", "PLATFORM_OWNER", "PLATFORM_SUPPORT"})
    void anyStaffRoleReadsTheTemplates(String role) {
        when(templates.list()).thenReturn(List.of());

        assertThat(controller.list(role).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT_OWNER", "PLATFORM_OWNER"})
    void theWorkspaceOwnerDeletesATemplate(String role) {
        assertThat(controller.delete(role, "custom").getStatusCode().is2xxSuccessful()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PLATFORM_ADMIN", "PLATFORM_SUPPORT"})
    void staffBelowTheOwnerAreToldTheOwnerRoleIsNeeded(String role) {
        assertThatThrownBy(() -> controller.delete(role, "custom"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Workspace owner role required");
        verifyNoInteractions(templates);
    }

    @Test
    void theRetiredSuperAdminRoleCannotReadOrWrite() {
        assertThatThrownBy(() -> controller.list("SUPER_ADMIN"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Admin role required");
        assertThatThrownBy(() -> controller.delete("SUPER_ADMIN", "custom"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Workspace owner role required");
    }
}
