package com.civileng.marketplace.notification.model;

/**
 * Lifecycle of one email send attempt.
 *
 * <p>The first four are ours; {@link #DELIVERED} and {@link #UNDELIVERED} are only ever set by a
 * provider callback, so a row sitting at {@link #SENT} means "the provider accepted it and has not
 * told us what happened next" — which is the permanent resting state on SMTP, where nobody calls
 * back.
 */
public enum EmailStatus {
    /** Queued — rendered and handed to the sender, outcome not yet known. */
    PENDING,
    /** The provider accepted the message. */
    SENT,
    /** The provider confirmed it reached the mailbox. */
    DELIVERED,
    /** The provider confirmed it bounced, was blocked, or was rejected as spam. */
    UNDELIVERED,
    /** We could not hand it over at all — render error, or the provider refused it. */
    FAILED,
    /** No provider configured, so the mail was only logged. Not a delivery attempt. */
    SKIPPED
}
