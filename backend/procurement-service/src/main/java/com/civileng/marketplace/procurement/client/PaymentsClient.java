package com.civileng.marketplace.procurement.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * payment-service's internal order creation, for payments that are not a booking. The payer's
 * identity travels in the signed context headers, copied from the caller.
 */
@FeignClient(name = "payment-service", contextId = "procurementPayments", path = "/api/v1/payments/internal")
public interface PaymentsClient {

    @PostMapping("/orders")
    PaymentOrder createOrder(@RequestBody OrderRequest request);

    record OrderRequest(String referenceType, Long referenceId, BigDecimal amount, String description) { }

    /** What checkout needs, and the payment's state (FAILED carries the provider's reason). */
    record PaymentOrder(Long id, String paymentCode, String razorpayOrderId, String razorpayKeyId,
                        BigDecimal totalAmount, String paymentStatus, String failureReason) { }
}
