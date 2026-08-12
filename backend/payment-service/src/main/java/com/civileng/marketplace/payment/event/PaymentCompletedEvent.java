package com.civileng.marketplace.payment.event;

/**
 * In-process signal that a payment reached COMPLETED, so escrow can promote the hold it funds.
 *
 * <p>A Spring event rather than a direct call because EscrowService already depends on
 * PaymentService for order creation and refunds — injecting the reverse would be a cycle.
 */
public record PaymentCompletedEvent(Long paymentId, Long bookingId) {
}
