package com.civileng.marketplace.procurement.model;

/** MATCHED and EXCEPTION are the three-way match's verdict; APPROVED and REJECTED the buyer's decision; PAID once payment-service confirms the payment. */
public enum InvoiceStatus {
    MATCHED, EXCEPTION, APPROVED, REJECTED, PAID
}
