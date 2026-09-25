package com.civileng.marketplace.procurement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Marks a supplier invoice paid when payment-service reports its payment complete. Payments for
 * anything else (bookings) share the topic and are ignored. The tenant comes from the record's
 * header, set by the platform's record interceptor before this runs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventsListener {

    private final PurchaseOrderService purchaseOrders;

    @KafkaListener(topics = "payment.completed", groupId = "procurement-service-group")
    public void onPaymentCompleted(Map<String, Object> event) {
        if (!PurchaseOrderService.PAYMENT_REFERENCE_TYPE.equals(event.get("referenceType"))) {
            return;
        }
        Long invoiceId = asLong(event.get("referenceId"));
        if (invoiceId == null) {
            log.warn("Supplier-invoice payment without a referenceId: {}", event);
            return;
        }
        String reference = event.get("razorpayPaymentId") != null ? String.valueOf(event.get("razorpayPaymentId"))
                : String.valueOf(event.get("paymentCode"));
        purchaseOrders.markPaid(invoiceId, asLong(event.get("paymentId")), reference);
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? null : Long.valueOf(v.toString());
    }
}
