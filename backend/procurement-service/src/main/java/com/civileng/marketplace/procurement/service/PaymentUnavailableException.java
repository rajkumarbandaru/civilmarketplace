package com.civileng.marketplace.procurement.service;

/** payment-service could not be reached or failed. A 503: the buyer can try again shortly. */
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException() {
        super("Payments are unavailable right now. Please try again shortly.");
    }
}
