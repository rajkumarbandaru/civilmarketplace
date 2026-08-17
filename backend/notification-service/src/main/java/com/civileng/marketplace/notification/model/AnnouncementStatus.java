package com.civileng.marketplace.notification.model;

/**
 * Where an announcement is between being written and having landed in every recipient's bell.
 *
 * An immediate send never passes through {@link #SCHEDULED} — it is created already {@link #SENT}.
 */
public enum AnnouncementStatus {

    /** Written, waiting for {@code scheduledAt}. Its audience is not resolved yet. */
    SCHEDULED,

    /**
     * Claimed by a scheduler and fanning out right now.
     *
     * This state is what stops two service instances sending the same announcement twice: claiming
     * is a conditional UPDATE off SCHEDULED, so only one instance's claim can win.
     */
    SENDING,

    /** Delivered. {@code recipientCount} and {@code sentAt} are final. */
    SENT,

    /** Called off before its time. Never fans out; kept for the history table. */
    CANCELLED
}
