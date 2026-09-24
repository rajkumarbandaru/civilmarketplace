package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantProvisioning;
import com.civileng.marketplace.tenant.model.TenantProvisioning.Step;
import com.civileng.marketplace.tenant.model.TenantServiceAck;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.model.Vertical;
import com.civileng.marketplace.tenant.repository.TenantProvisioningRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.civileng.marketplace.tenant.repository.TenantServiceAckRepository;
import com.civileng.marketplace.tenant.repository.TenantStatusChangeRepository;
import com.civileng.marketplace.tenant.service.TenantLifecycle;
import com.civileng.marketplace.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProvisioningServiceTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantProvisioningRepository sagas = mock(TenantProvisioningRepository.class);
    private final TenantServiceAckRepository acks = mock(TenantServiceAckRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final AuthProvisioningClient auth = mock(AuthProvisioningClient.class);
    private final Map<String, TenantProvisioning> sagaRows = new HashMap<>();
    private final Map<TenantServiceAck.Key, TenantServiceAck> ackRows = new HashMap<>();
    private final FactoryProperties props = new FactoryProperties(List.of("auth-service", "user-service"),
            Duration.ofMinutes(10), 3, "http://{subdomain}.localhost:3000");
    private Instant now = Instant.parse("2026-09-24T10:00:00Z");
    private ProvisioningService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(inv -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        tenant = Tenant.builder().tenantKey("acme").name("Acme Builders").subdomain("acme").status(TenantStatus.DRAFT)
                .vertical(Vertical.CIVIL_MARKETPLACE).enabledModules("auth,bookings").ownerName("Asha")
                .ownerEmail("asha@acme.in").contactEmail("ops@acme.in").build();
        when(tenantService.byKey("acme")).thenReturn(tenant);
        when(tenants.findByTenantKey("acme")).thenReturn(Optional.of(tenant));

        when(sagas.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(sagaRows.get(inv.<String>getArgument(0))));
        when(sagas.save(any())).thenAnswer(inv -> {
            TenantProvisioning p = inv.getArgument(0);
            sagaRows.put(p.getTenantKey(), p);
            return p;
        });
        when(sagas.findByStepIn(anyCollection())).thenAnswer(inv -> sagaRows.values().stream()
                .filter(p -> inv.<Collection<?>>getArgument(0).contains(p.getStep())).toList());
        when(acks.save(any())).thenAnswer(inv -> {
            TenantServiceAck a = inv.getArgument(0);
            ackRows.put(a.getId(), a);
            return a;
        });
        when(acks.findByIdTenantKey("acme")).thenAnswer(inv -> new ArrayList<>(ackRows.values()));
        doAnswer(inv -> { ackRows.clear(); return null; }).when(acks).deleteByTenantKey("acme");

        service = new ProvisioningService(tenants, sagas, acks, new TenantLifecycle(mock(TenantStatusChangeRepository.class)),
                tenantService, auth, props, clock, tx);
    }

    private void ack(String serviceName, boolean ok, String error) {
        service.onAck("{\"tenantKey\":\"acme\",\"service\":\"" + serviceName + "\",\"ok\":" + ok
                + (error == null ? "" : ",\"error\":\"" + error + "\"") + ",\"at\":1}");
    }

    private TenantProvisioning saga() {
        return sagaRows.get("acme");
    }

    @Test
    void publishAnnouncesTheTenantWithItsBrandingAndWaits() {
        service.publish("acme", "1");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(saga().getStep()).isEqualTo(Step.AWAIT_SCHEMAS);
        verify(tenantService).announce(tenant, true);
        service.tick();
        assertThat(saga().getStep()).as("nobody acked yet").isEqualTo(Step.AWAIT_SCHEMAS);
    }

    @Test
    void goesLiveOnlyAfterEveryServiceAndInvitesTheOwnerLast() {
        when(auth.ensureOwner("acme", "1", "Asha", "asha@acme.in")).thenReturn(new AuthProvisioningClient.Owner(42L, "asha@acme.in", true));
        service.publish("acme", "1");
        ack("auth-service", true, null);
        service.tick();
        assertThat(saga().getStep()).isEqualTo(Step.AWAIT_SCHEMAS);

        ack("user-service", true, null);
        service.tick(); // -> CREATE_OWNER
        service.tick(); // owner created -> ACTIVATE
        assertThat(saga().getOwnerUserId()).isEqualTo(42L);
        verify(auth, never()).invite(any(), any(), any(), any(), any());
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);

        service.tick(); // -> ACTIVE
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantService).announce(tenant, false);

        service.tick(); // invitation
        verify(auth).invite("acme", "1", 42L, "http://acme.localhost:3000", "Acme Builders");
        assertThat(saga().getStep()).isEqualTo(Step.DONE);
        assertThat(saga().getFinishedAt()).isNotNull();
    }

    @Test
    void aServiceThatCannotProvisionFailsThePublish() {
        service.publish("acme", "1");
        ack("user-service", false, "Flyway: syntax error");
        service.tick();
        assertThat(saga().getStep()).isEqualTo(Step.FAILED);
        assertThat(saga().getLastError()).contains("user-service could not provision: Flyway");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
    }

    @Test
    void timesOutNamingTheSilentServicesAndCanBeRetried() {
        service.publish("acme", "1");
        ack("auth-service", true, null);
        now = now.plus(Duration.ofMinutes(11));
        service.tick();
        assertThat(saga().getLastError()).isEqualTo("Timed out waiting for user-service");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);

        service.publish("acme", "1");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(saga().getStep()).isEqualTo(Step.AWAIT_SCHEMAS);
        assertThat(ackRows).as("acks start over").isEmpty();
    }

    @Test
    void ownerCreationRetriesThenFails() {
        when(auth.ensureOwner(any(), any(), any(), any())).thenThrow(new RuntimeException("auth-service down"));
        service.publish("acme", "1");
        ack("auth-service", true, null);
        ack("user-service", true, null);
        service.tick();
        service.tick();
        service.tick();
        assertThat(saga().getStep()).isEqualTo(Step.CREATE_OWNER);
        assertThat(saga().getAttempts()).isEqualTo(2);
        service.tick();
        assertThat(saga().getStep()).isEqualTo(Step.FAILED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
    }

    @Test
    void aFailedInvitationLeavesTheTenantLiveAndCanBeResent() {
        when(auth.ensureOwner(any(), any(), any(), any())).thenReturn(new AuthProvisioningClient.Owner(42L, "asha@acme.in", true));
        doThrow(new RuntimeException("kafka down")).when(auth).invite(any(), any(), any(), any(), any());
        service.publish("acme", "1");
        ack("auth-service", true, null);
        ack("user-service", true, null);
        for (int i = 0; i < 6; i++) service.tick();
        assertThat(saga().getStep()).isEqualTo(Step.FAILED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);

        doNothing().when(auth).invite(any(), any(), any(), any(), any());
        service.resendInvitation("acme", "1");
        assertThat(saga().getStep()).isEqualTo(Step.DONE);
    }

    @Test
    void refusesToPublishWithoutAnOwnerOrTwice() {
        tenant.setOwnerEmail(null);
        assertThatThrownBy(() -> service.publish("acme", "1")).hasMessageContaining("owner's email");
        tenant.setOwnerEmail("asha@acme.in");
        tenant.setStatus(TenantStatus.ACTIVE);
        assertThatThrownBy(() -> service.publish("acme", "1")).hasMessageContaining("Only a DRAFT tenant");
    }

    @Test
    void acksForTenantsNotWaitingAreIgnored() {
        ack("auth-service", true, null);
        assertThat(ackRows).isEmpty();
    }

    @Test
    void progressShowsEachService() {
        service.publish("acme", "1");
        ack("auth-service", true, null);
        var view = service.view("acme");
        assertThat(view.services()).extracting(ProvisioningService.ServiceState::state).containsExactly("READY", "WAITING");
        assertThat(view.step()).isEqualTo("AWAIT_SCHEMAS");
    }
}
