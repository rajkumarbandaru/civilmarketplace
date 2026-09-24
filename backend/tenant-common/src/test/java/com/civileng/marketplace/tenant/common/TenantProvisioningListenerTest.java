package com.civileng.marketplace.tenant.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantProvisioningListenerTest {

    private final TenantSchemaMigrator migrator = mock(TenantSchemaMigrator.class);
    private final TenantProvisioningAcks acks = mock(TenantProvisioningAcks.class);
    private final TenantProvisioningListener listener = new TenantProvisioningListener(migrator, List.of(), acks);

    private static TenantEventMessage event(String status) {
        return TenantEventMessage.builder().tenantKey("acme").status(status).build();
    }

    @Test
    void provisionsATenantBeingPublishedAndSaysSo() {
        listener.onTenantEvent(event("PROVISIONING"));
        verify(migrator).migrate("acme");
        verify(acks).send("acme", true, null);
    }

    @Test
    void reportsAFailureInsteadOfStayingSilent() {
        doThrow(new IllegalStateException("disk full")).when(migrator).migrate("acme");
        listener.onTenantEvent(event("PROVISIONING"));
        verify(acks).send(eq("acme"), eq(false), contains("disk full"));
    }

    @Test
    void aDraftHasNothingToProvision() {
        listener.onTenantEvent(event("DRAFT"));
        verifyNoInteractions(migrator, acks);
    }
}
