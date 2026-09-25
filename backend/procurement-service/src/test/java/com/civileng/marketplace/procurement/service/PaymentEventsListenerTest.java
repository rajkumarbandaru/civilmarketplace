package com.civileng.marketplace.procurement.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

class PaymentEventsListenerTest {

    private final PurchaseOrderService orders = mock(PurchaseOrderService.class);
    private final PaymentEventsListener listener = new PaymentEventsListener(orders);

    @Test
    void supplierInvoicePaymentsMarkTheInvoicePaid() {
        listener.onPaymentCompleted(Map.of("referenceType", "SUPPLIER_INVOICE", "referenceId", 40, "paymentId", 900,
                "razorpayPaymentId", "pay_ABC", "paymentCode", "PAY1"));
        verify(orders).markPaid(40L, 900L, "pay_ABC");
    }

    @Test
    void bookingPaymentsAreNotOurs() {
        listener.onPaymentCompleted(Map.of("referenceType", "BOOKING", "referenceId", 5, "paymentId", 1));
        listener.onPaymentCompleted(Map.of("bookingId", 5, "paymentId", 1));
        verifyNoInteractions(orders);
    }
}
