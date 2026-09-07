package com.civileng.marketplace.tenant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Republishes every tenant's state once this service is up.
 *
 * <p>Other services keep a synced copy of each tenant's module set, because the menu has to be
 * filtered inside the tenant's own schema and there is nothing to ask at request time. A synced
 * copy is only as correct as the last message that service was running to receive — a consumer
 * that was down for one event stays wrong forever otherwise, and the symptom (a tenant seeing a
 * tab it should not) looks nothing like the cause.
 *
 * <p>Broadcasting the truth on every boot makes that self-correcting. Every consumer is
 * idempotent, so a redundant broadcast costs a few no-op writes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStateBroadcaster {

    private final TenantService tenantService;

    @EventListener(ApplicationReadyEvent.class)
    public void broadcastOnStartup() {
        try {
            tenantService.republishAll();
        } catch (RuntimeException e) {
            // Never fatal: the service can serve every request without this, and failing to start
            // over a best-effort resync would turn a Kafka hiccup into an outage.
            log.error("Could not republish tenant state on startup", e);
        }
    }
}
