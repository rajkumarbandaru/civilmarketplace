package com.civileng.marketplace.payment.model;

public enum WalletTransactionType {
    /** Escrow released to the payee, net of commission. */
    ESCROW_RELEASE,
    CREDIT,
    DEBIT,
    WITHDRAWAL,
    REFUND,
    /** Balance frozen behind an open dispute, and its reversal. */
    HOLD,
    HOLD_RELEASE
}
