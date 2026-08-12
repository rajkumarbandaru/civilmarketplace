package com.civileng.marketplace.payment.model;

/** SRS CP·06 FR-06. */
public enum EscrowStatus {
    /** Created, but the funding payment has not completed at the PSP yet. */
    PENDING_FUNDING,
    /** Funded and claimable by neither party until released or refunded. */
    HELD,
    RELEASED,
    REFUNDED,
    CANCELLED,
    /** FR-09: an open dispute freezes the hold — no release, manual or automatic. */
    DISPUTED;

    public boolean isTerminal() {
        return this == RELEASED || this == REFUNDED || this == CANCELLED;
    }
}
