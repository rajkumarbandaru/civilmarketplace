package com.civileng.marketplace.tenant.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * Runs work once per active tenant.
 *
 * <p>Scheduled jobs are the reason this exists. A cron job fires on a thread with no tenant bound,
 * and before multi-tenancy each job swept "all the rows" in one schema. That same job now has to
 * sweep every tenant's schema, or the auto-release timer and announcement scheduler quietly stop
 * working for every tenant except the bootstrap one.
 *
 * <p>The tenant list is re-read per sweep rather than cached from startup, so a tenant onboarded
 * an hour ago is included without a restart. One tenant's failure is logged and the sweep
 * continues — a single bad tenant must not stop the job for the rest.
 */
@Slf4j
@RequiredArgsConstructor
public class CrossTenantRunner {

    private final TenantRegistry registry;

    public void forEachTenant(String jobName, Consumer<String> work) {
        List<String> tenantKeys;
        try {
            tenantKeys = registry.activeTenantKeys();
        } catch (RuntimeException e) {
            log.error("{}: could not read the tenant registry, skipping this run", jobName, e);
            return;
        }

        for (String tenantKey : tenantKeys) {
            try {
                TenantContext.runAs(tenantKey, () -> work.accept(tenantKey));
            } catch (RuntimeException e) {
                log.error("{}: failed for tenant '{}'", jobName, tenantKey, e);
            }
        }
    }
}
