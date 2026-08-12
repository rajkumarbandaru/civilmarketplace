package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Processing", description = "Payment and Razorpay integration APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    @Operation(summary = "Create payment order")
    public ResponseEntity<Payment> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> request) {
        Long bookingId = Long.valueOf(request.get("bookingId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPaymentOrder(bookingId, userId, amount));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify payment and complete")
    public ResponseEntity<Payment> verifyPayment(
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(paymentService.verifyAndCompletePayment(
                request.get("razorpayOrderId"),
                request.get("razorpayPaymentId"),
                request.get("razorpaySignature")));
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Process refund")
    public ResponseEntity<Payment> refund(
            @PathVariable Long paymentId,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String reason = (String) request.getOrDefault("reason", "");
        return ResponseEntity.ok(
                paymentService.processRefund(paymentId, amount, reason));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment by booking")
    public ResponseEntity<Payment> getByBooking(
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.getPaymentByBooking(bookingId));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Razorpay webhook handler")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        paymentService.handleWebhookEvent(payload, signature);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "payment-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
