package com.civileng.marketplace.notification.scheduler;

import com.civileng.marketplace.notification.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends announcements whose scheduled time has arrived.
 *
 * Runs every minute, which is the resolution the console offers — the picker takes a time to the
 * minute, so checking more often would only find the same rows sooner than anyone asked for.
 * Delivery is therefore accurate to within a minute of the appointed time, never early.
 *
 * Each announcement is released in its own transaction rather than the whole batch in one: a role
 * lookup that fails partway through a large fan-out should cost that one announcement, not undo
 * the ones already sent alongside it.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnnouncementReleaseJob {

    private final AnnouncementService announcementService;

    @Scheduled(cron = "${announcements.release-cron:0 * * * * *}")
    public void releaseDue() {
        List<Long> due = announcementService.findDue();
        if (due.isEmpty()) {
            return;
        }
        log.info("Releasing {} scheduled announcement(s)", due.size());
        for (Long id : due) {
            try {
                announcementService.release(id);
            } catch (Exception ex) {
                // Left SCHEDULED by the rollback, so the next run retries it. Logged rather than
                // rethrown so one bad announcement does not stop the rest of this batch.
                log.error("Failed to release announcement {}: {}", id, ex.getMessage(), ex);
            }
        }
    }
}
