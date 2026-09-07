package com.civileng.marketplace.payment.scheduler;

import com.civileng.marketplace.payment.model.EscrowHold;
import com.civileng.marketplace.payment.service.EscrowService;
import com.civileng.marketplace.tenant.common.CrossTenantRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CP·06 FR-06's auto-release timer: a funded hold the payer never confirmed releases itself once
 * its window elapses, so a silent payer cannot strand a provider's money indefinitely.
 *
 * <p>Each hold releases in its own transaction — one failure must not abort the sweep. Disputed
 * holds are excluded by the query itself rather than by a filter here.
 *
 * <p>The sweep runs once per tenant. A cron thread has no tenant bound, so without the explicit
 * fan-out this job would only ever release the bootstrap tenant's holds and every other tenant's
 * escrow would sit funded forever.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EscrowAutoReleaseJob {

    private final EscrowService escrowService;
    private final CrossTenantRunner crossTenantRunner;

    @Scheduled(cron = "${escrow.auto-release-cron:0 */10 * * * *}")
    public void releaseDueHolds() {
        crossTenantRunner.forEachTenant("escrow-auto-release", tenant -> releaseDueHoldsFor(tenant));
    }

    private void releaseDueHoldsFor(String tenant) {
        List<EscrowHold> due = escrowService.findDueForAutoRelease();
        if (due.isEmpty()) return;

        log.info("Auto-releasing {} escrow hold(s) past their timer for tenant '{}'",
                due.size(), tenant);
        for (EscrowHold hold : due) {
            try {
                escrowService.autoRelease(hold.getId());
            } catch (RuntimeException e) {
                log.error("Auto-release failed for escrow {}: {}",
                        hold.getEscrowCode(), e.getMessage());
            }
        }
    }
}
