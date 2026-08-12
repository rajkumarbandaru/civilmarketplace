package com.civileng.marketplace.payment.model;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    AUTHORIZED,
    CAPTURED,
    COMPLETED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CANCELLED
}
