package com.civileng.marketplace.payment.scheduler;

import com.civileng.marketplace.payment.model.EscrowHold;
import com.civileng.marketplace.payment.service.EscrowService;
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
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EscrowAutoReleaseJob {

    private final EscrowService escrowService;

    @Scheduled(cron = "${escrow.auto-release-cron:0 */10 * * * *}")
    public void releaseDueHolds() {
        List<EscrowHold> due = escrowService.findDueForAutoRelease();
        if (due.isEmpty()) return;

        log.info("Auto-releasing {} escrow hold(s) past their timer", due.size());
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
