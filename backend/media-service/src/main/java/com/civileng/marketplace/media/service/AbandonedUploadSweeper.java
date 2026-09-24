package com.civileng.marketplace.media.service;

import com.civileng.marketplace.tenant.common.CrossTenantRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly, every tenant: removes upload slots that were never completed. */
@Component
@Slf4j
@RequiredArgsConstructor
public class AbandonedUploadSweeper {

    private final CrossTenantRunner tenants;
    private final MediaService mediaService;

    @Scheduled(cron = "${media.sweep-cron:0 17 * * * *}")
    public void sweep() {
        tenants.forEachTenant("media-abandoned-sweep", tenant -> {
            int removed = mediaService.sweepAbandoned();
            if (removed > 0) {
                log.info("Removed {} abandoned upload(s) for tenant {}", removed, tenant);
            }
        });
    }
}
