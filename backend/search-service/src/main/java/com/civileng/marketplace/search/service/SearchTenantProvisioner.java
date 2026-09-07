package com.civileng.marketplace.search.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.TenantEventMessage;
import com.civileng.marketplace.tenant.common.TenantProvisionedCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Builds a new tenant's search indices as soon as the tenant goes ACTIVE, rather than leaving it
 * unsearchable until the next scheduled sweep.
 *
 * <p>The sibling services provision a tenant by migrating a schema; this one has no schema, so its
 * equivalent is a first reindex — which creates both indices with their mappings.
 *
 * <p>That first reindex reads the tenant's data out of auth, user, booking and review services,
 * and every one of those is provisioning the same tenant from the same Kafka message at the same
 * moment. Whoever migrates second answers a read against a schema whose tables do not exist yet
 * with a 500, so the attempt is retried on a delay instead of being taken as a real failure. The
 * scheduled sweep is still the backstop; the retries are what keep a newly onboarded tenant from
 * looking empty for the five minutes until it runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTenantProvisioner implements TenantProvisionedCallback {

    private final ReindexService reindexService;
    private final TaskScheduler taskScheduler;

    @Value("${search.provision-retries:5}")
    private int maxAttempts;

    @Value("${search.provision-retry-delay-seconds:15}")
    private long retryDelaySeconds;

    @Override
    public void onProvisioned(TenantEventMessage event) {
        attempt(event.getTenantKey(), 1);
    }

    /**
     * Runs off the Kafka listener thread from the first attempt: one tenant's slow reindex must not
     * hold up the provisioning of the next tenant's, and the retries need the same thread anyway.
     */
    private void attempt(String tenantKey, int attempt) {
        taskScheduler.schedule(() -> {
            try {
                TenantContext.runAs(tenantKey, () -> {
                    log.info("Building search indices for new tenant '{}' (attempt {}/{})",
                            tenantKey, attempt, maxAttempts);
                    reindexService.reindexCurrentTenant();
                });
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts) {
                    log.error("Could not build search indices for new tenant '{}' after {} "
                            + "attempts — the scheduled sweep will pick it up", tenantKey,
                            maxAttempts, e);
                    return;
                }
                log.warn("Building search indices for tenant '{}' failed on attempt {}/{} ({}) —"
                        + " retrying in {}s", tenantKey, attempt, maxAttempts, e.getMessage(),
                        retryDelaySeconds);
                attempt(tenantKey, attempt + 1);
            }
        }, Instant.now().plus(Duration.ofSeconds(attempt == 1 ? 0 : retryDelaySeconds)));
    }
}
