package com.civileng.marketplace.admin.service;

import com.civileng.marketplace.admin.client.PaymentServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The console's invoice screen, served from payment-service.
 *
 * <p>Payment-service owns the money, so nothing is recomputed here — this is a gateway with a
 * fallback. Unlike the revenue screen, an empty invoice list is served when payment-service is
 * unreachable rather than sample figures: a finance screen that invents rows is worse than one
 * that says it could not load, because the numbers would be indistinguishable from real ones.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminInvoiceService {

    private final PaymentServiceClient paymentServiceClient;

    @CircuitBreaker(name = "invoiceService", fallbackMethod = "invoicesFallback")
    public Map<String, Object> getInvoices(int page, int size, String status, String search) {
        Map<String, Object> response = paymentServiceClient.getInvoices(page, size, status, search).getBody();
        if (response != null) return response;
        return invoicesFallback(page, size, status, search, new IllegalStateException("empty body"));
    }

    @CircuitBreaker(name = "invoiceService", fallbackMethod = "summaryFallback")
    public Map<String, Object> getSummary() {
        Map<String, Object> response = paymentServiceClient.getInvoiceSummary().getBody();
        if (response != null) return response;
        return summaryFallback(new IllegalStateException("empty body"));
    }

    /**
     * Raises an invoice in payment-service, which owns the money.
     *
     * <p>Deliberately not behind a circuit breaker fallback: a fallback would answer "couldn't
     * reach payment-service" with a 200 body, and a write that may or may not have landed must not
     * look like a success. The Feign error propagates instead, so the caller learns it failed.
     */
    public Map<String, Object> raiseInvoice(Map<String, Object> command) {
        Map<String, Object> response = paymentServiceClient.raiseInvoice(command).getBody();
        if (response == null) {
            throw new IllegalStateException("payment-service returned an empty body");
        }
        return response;
    }

    @CircuitBreaker(name = "invoiceService", fallbackMethod = "invoiceFallback")
    public Map<String, Object> getInvoice(String invoiceNumber) {
        Map<String, Object> response = paymentServiceClient.getInvoice(invoiceNumber).getBody();
        if (response != null) return response;
        return invoiceFallback(invoiceNumber, new IllegalStateException("empty body"));
    }

    // ------------------------------------------------------------------ fallbacks

    @SuppressWarnings("unused")
    private Map<String, Object> invoicesFallback(int page, int size, String status, String search, Throwable t) {
        log.warn("Could not load invoices from payment-service: {}", t.getMessage());
        return Map.of(
                "success", false,
                "message", "Invoices are unavailable right now — payment-service could not be reached",
                "data", List.of(),
                "page", page, "size", size,
                "totalElements", 0, "totalPages", 0);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> summaryFallback(Throwable t) {
        log.warn("Could not load the invoice summary from payment-service: {}", t.getMessage());
        return Map.of(
                "success", false,
                "message", "Invoice totals are unavailable right now",
                "data", Map.of(
                        "totalBilled", 0, "totalCollected", 0, "totalOutstanding", 0,
                        "totalRefunded", 0, "invoiceCount", 0, "paidCount", 0,
                        "pendingCount", 0, "refundedCount", 0, "failedCount", 0));
    }

    @SuppressWarnings("unused")
    private Map<String, Object> invoiceFallback(String invoiceNumber, Throwable t) {
        log.warn("Could not load invoice {} from payment-service: {}", invoiceNumber, t.getMessage());
        return Map.of(
                "success", false,
                "message", "Invoice " + invoiceNumber + " could not be loaded right now");
    }
}
