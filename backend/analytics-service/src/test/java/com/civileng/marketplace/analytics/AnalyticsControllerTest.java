package com.civileng.marketplace.analytics;

import com.civileng.marketplace.analytics.api.AnalyticsController;
import com.civileng.marketplace.analytics.cdc.CaptureManager;
import com.civileng.marketplace.analytics.warehouse.Kpis;
import com.civileng.marketplace.analytics.warehouse.Projector;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AnalyticsControllerTest {

    private final Kpis kpis = mock(Kpis.class);
    private final Projector projector = mock(Projector.class);
    private final AnalyticsController controller = new AnalyticsController(kpis, mock(CaptureManager.class), projector);

    @Test
    void aWorkspacesStaffSeeTheirOwnTenantAndNoParameterCanChangeWhich() {
        controller.workspace("acme", "ADMIN");
        verify(kpis).workspace("acme");
        assertThatThrownBy(() -> controller.workspace("acme", "CUSTOMER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.workspace(null, "ADMIN")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void crossTenantFiguresAreForRkPlatformStaffOnly() {
        assertThatThrownBy(() -> controller.platform("acme", "TENANT_OWNER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.platform("acme", "PLATFORM_OWNER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.platform("platform", "ADMIN")).isInstanceOf(AccessDeniedException.class);
        controller.platform("platform", "PLATFORM_OWNER");
        verify(kpis).platform();
        assertThatThrownBy(() -> controller.capture("acme", "PLATFORM_ADMIN")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void platformSupportMayReadTheFiguresButNotPurge() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(
                        new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/analytics/platform")));
        try {
            controller.platform("platform", "PLATFORM_SUPPORT");
            verify(kpis).platform();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
        assertThatThrownBy(() -> controller.purge("oldco", "platform", "PLATFORM_SUPPORT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTenantsPartitionIsPurgedOnlyByTheOperatorAndNeverThePlatformsOwn() {
        when(projector.purge("oldco")).thenReturn(12);
        assertThat(controller.purge("oldco", "platform", "PLATFORM_ADMIN")).containsEntry("rowsDeleted", 12);
        assertThatThrownBy(() -> controller.purge("oldco", "oldco", "TENANT_OWNER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.purge("platform", "platform", "PLATFORM_OWNER")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.purge("x' OR '1'='1", "platform", "PLATFORM_OWNER")).isInstanceOf(IllegalArgumentException.class);
    }
}
