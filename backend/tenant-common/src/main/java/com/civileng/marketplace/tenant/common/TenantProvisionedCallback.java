package com.civileng.marketplace.tenant.common;

/**
 * Work a service wants to run against a tenant's schema immediately after that schema is created.
 *
 * <p>Flyway leaves a tenant's tables empty, which is right for almost everything — but a few rows
 * are part of provisioning rather than of a migration, because their values come from the
 * onboarding request rather than being the same for every tenant. admin-service uses this to seed
 * the theme the operator chose.
 *
 * <p>Implementations are called on the Kafka listener thread, <em>after</em> a successful migrate
 * and with no tenant bound to the thread — use {@link TenantContext#runAs} for anything touching
 * JPA. A callback that throws is logged and skipped: a service failing to seed its own optional
 * rows must not leave the tenant unprovisioned everywhere else.
 */
@FunctionalInterface
public interface TenantProvisionedCallback {

    void onProvisioned(TenantEventMessage event);
}
