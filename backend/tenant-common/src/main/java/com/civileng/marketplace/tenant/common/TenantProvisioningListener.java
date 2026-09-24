package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;

/**
 * Provisions this service's storage for a tenant created after the service booted, so onboarding a
 * tenant does not mean restarting eleven services. Idempotent: Flyway on an already-current schema
 * is a no-op, so a redelivered event costs nothing.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantProvisioningListener {

    /**
     * Null in a service with no schema layer — search-service keeps its per-tenant state in
     * Elasticsearch, so there is no schema to migrate but there is still setup to run in the
     * callbacks below.
     */
    private final TenantSchemaMigrator migrator;

    /** Post-provision work this service wants run against the new schema; usually empty. */
    private final List<TenantProvisionedCallback> callbacks;

    /** Reports the outcome to tenant-service's provisioning saga. Null where Kafka is absent. */
    private final TenantProvisioningAcks acks;

    /** Statuses that need storage: a tenant being provisioned, and live ones (idempotent re-sync). */
    private static final java.util.Set<String> PROVISIONABLE = java.util.Set.of("ACTIVE", "PROVISIONING", "PENDING");

    @KafkaListener(
            topics = TenantTopics.TENANT_EVENTS,
            groupId = "${spring.application.name}-tenant-provisioning",
            containerFactory = "tenantEventListenerContainerFactory")
    public void onTenantEvent(TenantEventMessage event) {
        if (!PROVISIONABLE.contains(event.getStatus())) {
            log.info("Tenant '{}' is {} — nothing to provision", event.getTenantKey(),
                    event.getStatus());
            return;
        }
        try {
            if (migrator != null) {
                migrator.migrate(event.getTenantKey());
            }
            log.info("Provisioned tenant '{}'", event.getTenantKey());
            runCallbacks(event);
            ack(event.getTenantKey(), true, null);
        } catch (RuntimeException e) {
            ack(event.getTenantKey(), false, e.getClass().getSimpleName() + ": " + e.getMessage());
            // Left unacked-and-logged rather than rethrown: a failed provision for one tenant must
            // not stall the listener for every other tenant's events.
            log.error("Failed to provision tenant '{}'", event.getTenantKey(), e);
        }
    }

    private void ack(String tenantKey, boolean ok, String error) {
        if (acks != null) acks.send(tenantKey, ok, error);
    }

    /**
     * Each callback is isolated: seeding is optional extra on top of a schema that already
     * migrated successfully, so one implementation failing must not turn a provisioned tenant
     * into a logged failure.
     */
    private void runCallbacks(TenantEventMessage event) {
        for (TenantProvisionedCallback callback : callbacks) {
            try {
                callback.onProvisioned(event);
            } catch (RuntimeException e) {
                log.error("Post-provision callback {} failed for tenant '{}'",
                        callback.getClass().getSimpleName(), event.getTenantKey(), e);
            }
        }
    }
}
