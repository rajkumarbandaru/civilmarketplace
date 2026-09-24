package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatusChange;
import com.civileng.marketplace.tenant.repository.TenantStatusChangeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.civileng.marketplace.tenant.model.TenantStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantLifecycleTest {

    private final TenantStatusChangeRepository history = mock(TenantStatusChangeRepository.class);
    private final TenantLifecycle lifecycle = new TenantLifecycle(history);

    private static Tenant tenant(String key, com.civileng.marketplace.tenant.model.TenantStatus status) {
        return Tenant.builder().tenantKey(key).status(status).build();
    }

    @Test
    void followsTheStateMachine() {
        assertThat(TenantLifecycle.allowed(DRAFT, PROVISIONING)).isTrue();
        assertThat(TenantLifecycle.allowed(PROVISIONING, ACTIVE)).isTrue();
        assertThat(TenantLifecycle.allowed(PROVISIONING_FAILED, PROVISIONING)).isTrue();
        assertThat(TenantLifecycle.allowed(ACTIVE, SUSPENDED)).isTrue();
        assertThat(TenantLifecycle.allowed(ARCHIVED, ACTIVE)).isTrue();

        assertThat(TenantLifecycle.allowed(DRAFT, ACTIVE)).as("no skipping provisioning").isFalse();
        assertThat(TenantLifecycle.allowed(ACTIVE, DRAFT)).isFalse();
        assertThat(TenantLifecycle.allowed(SUSPENDED, PROVISIONING)).isFalse();
    }

    @Test
    void recordsEveryTransitionWithActorAndReason() {
        Tenant t = tenant("acme", ACTIVE);
        lifecycle.transition(t, SUSPENDED, "7", "Unpaid");
        assertThat(t.getStatus()).isEqualTo(SUSPENDED);
        ArgumentCaptor<TenantStatusChange> saved = ArgumentCaptor.forClass(TenantStatusChange.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue()).extracting(TenantStatusChange::getFromStatus, TenantStatusChange::getToStatus,
                TenantStatusChange::getActor, TenantStatusChange::getReason).containsExactly("ACTIVE", "SUSPENDED", "7", "Unpaid");
    }

    @Test
    void refusesIllegalMovesAndNeverSuspendsTheOperator() {
        assertThatThrownBy(() -> lifecycle.transition(tenant("acme", DRAFT), ACTIVE, "7", null))
                .hasMessageContaining("A DRAFT tenant cannot become ACTIVE");
        assertThatThrownBy(() -> lifecycle.transition(tenant("platform", ACTIVE), SUSPENDED, "7", null))
                .hasMessageContaining("operator tenant");
        verifyNoInteractions(history);
    }

    @Test
    void operatorsCannotSetFactoryStates() {
        assertThat(TenantLifecycle.OPERATOR_SETTABLE).containsExactlyInAnyOrder(ACTIVE, SUSPENDED, ARCHIVED);
    }
}
