package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.common.TenantProvisioningAck;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantProvisioning;
import com.civileng.marketplace.tenant.model.TenantProvisioning.Step;
import com.civileng.marketplace.tenant.model.TenantServiceAck;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.repository.TenantProvisioningRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.civileng.marketplace.tenant.repository.TenantServiceAckRepository;
import com.civileng.marketplace.tenant.service.TenantLifecycle;
import com.civileng.marketplace.tenant.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Publishing a tenant: the provisioning saga (architecture 03 §5).
 *
 * <p>DRAFT → PROVISIONING announces the tenant; every service builds its storage and acknowledges;
 * then the owner's account is created in the tenant, the tenant goes ACTIVE, and — last, so the
 * owner never gets a link to a half-built workspace — the invitation is sent. Progress is persisted
 * after every step and a scheduler advances it, so a restarted tenant-service picks up where it
 * stopped; every step is idempotent, so running one twice is harmless.
 */
@Service
@Slf4j
public class ProvisioningService {

    private static final Set<Step> RUNNING = EnumSet.of(Step.AWAIT_SCHEMAS, Step.CREATE_OWNER, Step.ACTIVATE, Step.INVITE_OWNER);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TenantRepository tenants;
    private final TenantProvisioningRepository sagas;
    private final TenantServiceAckRepository acks;
    private final TenantLifecycle lifecycle;
    private final TenantService tenantService;
    private final AuthProvisioningClient auth;
    private final FactoryProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ProvisioningService(TenantRepository tenants, TenantProvisioningRepository sagas, TenantServiceAckRepository acks,
                               TenantLifecycle lifecycle, TenantService tenantService, AuthProvisioningClient auth,
                               FactoryProperties props, Clock clock, PlatformTransactionManager txManager) {
        this.tenants = tenants;
        this.sagas = sagas;
        this.acks = acks;
        this.lifecycle = lifecycle;
        this.tenantService = tenantService;
        this.auth = auth;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    public record ServiceState(String service, String state, String error) { }

    public record ProvisioningView(String tenantKey, String status, String step, int attempts, String lastError,
                                   LocalDateTime requestedAt, LocalDateTime finishedAt, List<ServiceState> services) { }

    /** Everything a tenant needs before it may be published. Empty means ready. */
    public List<String> publishBlockers(Tenant tenant) {
        List<String> problems = new ArrayList<>();
        String owner = tenant.getOwnerEmail();
        if (owner == null || owner.isBlank()) problems.add("Add the owner's email: they are invited to set a password at publish");
        else if (!owner.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) problems.add("The owner's email is not valid");
        if (!tenant.moduleKeys().contains("auth")) problems.add("A tenant needs the auth module");
        return problems;
    }

    /** DRAFT (or failed) → PROVISIONING: announce the tenant and start the saga. */
    @Transactional
    public ProvisioningView publish(String tenantKey, String actor) {
        Tenant tenant = tenantService.byKey(tenantKey);
        if (tenant.getStatus() != TenantStatus.DRAFT && tenant.getStatus() != TenantStatus.PROVISIONING_FAILED) {
            throw new IllegalArgumentException("Only a DRAFT tenant, or one whose provisioning failed, can be published");
        }
        List<String> blockers = publishBlockers(tenant);
        if (!blockers.isEmpty()) throw new IllegalArgumentException(String.join("; ", blockers));

        boolean retry = tenant.getStatus() == TenantStatus.PROVISIONING_FAILED;
        lifecycle.transition(tenant, TenantStatus.PROVISIONING, actor, retry ? "Provisioning retried" : "Published");
        acks.deleteByTenantKey(tenantKey);
        TenantProvisioning saga = sagas.findById(tenantKey).orElseGet(() -> TenantProvisioning.builder().tenantKey(tenantKey).build());
        saga.setStep(Step.AWAIT_SCHEMAS);
        saga.setAttempts(0);
        saga.setLastError(null);
        saga.setRequestedBy(actor);
        saga.setRequestedAt(LocalDateTime.now(clock));
        saga.setFinishedAt(null);
        sagas.save(saga);
        // With the branding: every service builds storage, and admin-service seeds the theme from it.
        tenantService.announce(tenant, true);
        log.info("Tenant '{}' {} by {}: waiting for {} services", tenantKey, retry ? "re-published" : "published",
                actor, props.requiredServices().size());
        return view(tenantKey);
    }

    /** A service's provisioning report. Recorded only while the tenant is waiting for them. */
    @KafkaListener(topics = FactoryConfig.ACK_TOPIC, groupId = "tenant-service-provisioning",
            containerFactory = "provisioningAckContainerFactory")
    public void onAck(String payload) {
        try {
            TenantProvisioningAck ack = JSON.readValue(payload, TenantProvisioningAck.class);
            tx.executeWithoutResult(s -> sagas.findById(ack.tenantKey())
                    .filter(saga -> saga.getStep() == Step.AWAIT_SCHEMAS)
                    .ifPresent(saga -> acks.save(new TenantServiceAck(
                            new TenantServiceAck.Key(ack.tenantKey(), ack.service()), ack.ok(),
                            truncate(ack.error()), LocalDateTime.now(clock)))));
        } catch (Exception e) {
            log.warn("Unreadable provisioning ack: {}", payload, e);
        }
    }

    /** Advances every tenant being provisioned by one step. Each in its own transaction. */
    @Scheduled(fixedDelayString = "${platform.factory.tick-ms:3000}", initialDelay = 10_000)
    public void tick() {
        for (TenantProvisioning saga : sagas.findByStepIn(RUNNING)) {
            try {
                tx.executeWithoutResult(s -> advance(saga.getTenantKey()));
            } catch (RuntimeException e) {
                log.error("Provisioning step failed unexpectedly for '{}'", saga.getTenantKey(), e);
            }
        }
    }

    void advance(String tenantKey) {
        TenantProvisioning saga = sagas.findById(tenantKey).orElseThrow();
        Tenant tenant = tenants.findByTenantKey(tenantKey).orElse(null);
        if (tenant == null) {
            sagas.delete(saga);
            return;
        }
        switch (saga.getStep()) {
            case AWAIT_SCHEMAS -> awaitSchemas(saga, tenant);
            case CREATE_OWNER -> attempt(saga, tenant, () -> {
                AuthProvisioningClient.Owner owner = auth.ensureOwner(tenantKey, saga.getRequestedBy(),
                        tenant.getOwnerName(), tenant.getOwnerEmail());
                saga.setOwnerUserId(owner.userId());
                saga.setStep(Step.ACTIVATE);
            });
            case ACTIVATE -> {
                lifecycle.transition(tenant, TenantStatus.ACTIVE, saga.getRequestedBy(), "Provisioned");
                tenantService.announce(tenant, false);
                saga.setStep(Step.INVITE_OWNER);
                saga.setAttempts(0);
            }
            case INVITE_OWNER -> attempt(saga, tenant, () -> {
                auth.invite(tenantKey, saga.getRequestedBy(), saga.getOwnerUserId(), props.appUrl(tenant.getSubdomain()),
                        tenant.getName());
                saga.setStep(Step.DONE);
                saga.setFinishedAt(LocalDateTime.now(clock));
                log.info("Tenant '{}' is live; owner invited", tenantKey);
            });
            default -> { }
        }
        sagas.save(saga);
    }

    private void awaitSchemas(TenantProvisioning saga, Tenant tenant) {
        Map<String, TenantServiceAck> byService = new HashMap<>();
        acks.findByIdTenantKey(saga.getTenantKey()).forEach(a -> byService.put(a.getId().getService(), a));
        for (TenantServiceAck a : byService.values()) {
            if (!a.isOk()) {
                fail(saga, tenant, a.getId().getService() + " could not provision: " + a.getError());
                return;
            }
        }
        List<String> missing = props.requiredServices().stream().filter(s -> !byService.containsKey(s)).toList();
        if (missing.isEmpty()) {
            saga.setStep(Step.CREATE_OWNER);
            saga.setAttempts(0);
        } else if (LocalDateTime.now(clock).isAfter(saga.getRequestedAt().plus(props.schemaTimeout()))) {
            fail(saga, tenant, "Timed out waiting for " + String.join(", ", missing));
        }
    }

    /** Runs a call to another service; failures retry on the next tick, up to maxAttempts. */
    private void attempt(TenantProvisioning saga, Tenant tenant, Runnable step) {
        try {
            step.run();
            saga.setLastError(null);
        } catch (RuntimeException e) {
            saga.setAttempts(saga.getAttempts() + 1);
            saga.setLastError(truncate(saga.getStep() + ": " + e.getMessage()));
            log.warn("Tenant '{}' step {} failed (attempt {}): {}", saga.getTenantKey(), saga.getStep(),
                    saga.getAttempts(), e.getMessage());
            if (saga.getAttempts() >= props.maxAttempts()) {
                fail(saga, tenant, saga.getLastError());
            }
        }
    }

    private void fail(TenantProvisioning saga, Tenant tenant, String error) {
        Step failedAt = saga.getStep();
        saga.setLastError(truncate(error));
        saga.setStep(Step.FAILED);
        saga.setFinishedAt(LocalDateTime.now(clock));
        // An invitation that could not be sent leaves a live tenant live: it can be re-sent.
        if (failedAt != Step.INVITE_OWNER && tenant.getStatus() == TenantStatus.PROVISIONING) {
            lifecycle.transition(tenant, TenantStatus.PROVISIONING_FAILED, saga.getRequestedBy(), error);
        }
        log.error("Provisioning of '{}' failed at {}: {}", saga.getTenantKey(), failedAt, error);
    }

    /** Sends the owner a fresh link (the old one stops working). For a live tenant only. */
    @Transactional
    public void resendInvitation(String tenantKey, String actor) {
        Tenant tenant = tenantService.byKey(tenantKey);
        if (tenant.getStatus() != TenantStatus.ACTIVE) throw new IllegalArgumentException("The tenant is not live yet");
        TenantProvisioning saga = sagas.findById(tenantKey)
                .orElseThrow(() -> new IllegalArgumentException("This tenant was not created through publishing"));
        Long owner = saga.getOwnerUserId() != null ? saga.getOwnerUserId()
                : auth.ensureOwner(tenantKey, actor, tenant.getOwnerName(), tenant.getOwnerEmail()).userId();
        auth.invite(tenantKey, actor, owner, props.appUrl(tenant.getSubdomain()), tenant.getName());
        saga.setOwnerUserId(owner);
        if (saga.getStep() == Step.FAILED) {
            saga.setStep(Step.DONE);
            saga.setLastError(null);
        }
        sagas.save(saga);
    }

    @Transactional(readOnly = true)
    public ProvisioningView view(String tenantKey) {
        Tenant tenant = tenantService.byKey(tenantKey);
        TenantProvisioning saga = sagas.findById(tenantKey).orElse(null);
        Map<String, TenantServiceAck> byService = new HashMap<>();
        acks.findByIdTenantKey(tenantKey).forEach(a -> byService.put(a.getId().getService(), a));
        boolean pastSchemas = saga != null && saga.getStep() != Step.AWAIT_SCHEMAS
                && !(saga.getStep() == Step.FAILED && byService.size() < props.requiredServices().size());
        List<ServiceState> services = props.requiredServices().stream().map(s -> {
            TenantServiceAck a = byService.get(s);
            String state = a == null ? (pastSchemas ? "READY" : "WAITING") : a.isOk() ? "READY" : "FAILED";
            return new ServiceState(s, state, a == null ? null : a.getError());
        }).toList();
        return saga == null
                ? new ProvisioningView(tenantKey, tenant.getStatus().name(), null, 0, null, null, null, services)
                : new ProvisioningView(tenantKey, tenant.getStatus().name(), saga.getStep().name(), saga.getAttempts(),
                        saga.getLastError(), saga.getRequestedAt(), saga.getFinishedAt(), services);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
