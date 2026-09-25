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
    void crossTenantFiguresAreForTheOperatorsSuperAdminsOnly() {
        assertThatThrownBy(() -> controller.platform("acme", "SUPER_ADMIN")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.platform("platform", "ADMIN")).isInstanceOf(AccessDeniedException.class);
        controller.platform("platform", "SUPER_ADMIN");
        verify(kpis).platform();
        assertThatThrownBy(() -> controller.capture("acme", "SUPER_ADMIN")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTenantsPartitionIsPurgedOnlyByTheOperatorAndNeverThePlatformsOwn() {
        when(projector.purge("oldco")).thenReturn(12);
        assertThat(controller.purge("oldco", "platform", "SUPER_ADMIN")).containsEntry("rowsDeleted", 12);
        assertThatThrownBy(() -> controller.purge("oldco", "oldco", "SUPER_ADMIN")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.purge("platform", "platform", "SUPER_ADMIN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.purge("x' OR '1'='1", "platform", "SUPER_ADMIN")).isInstanceOf(IllegalArgumentException.class);
    }
}
