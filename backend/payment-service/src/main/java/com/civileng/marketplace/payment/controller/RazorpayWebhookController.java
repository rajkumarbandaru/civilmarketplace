package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.service.PaymentService;
import com.civileng.marketplace.payment.service.RazorpayGateway;
import com.civileng.marketplace.tenant.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Razorpay webhooks, one URL per tenant: {@code /webhooks/payments/razorpay/{token}}.
 *
 * <p>A webhook has no JWT and no tenant header, and its body is Razorpay's, not ours — none of
 * which can say which tenant it is for. The opaque token each tenant's integration is issued can.
 * It picks the tenant, and with it the only webhook secret the signature may be checked against,
 * so tenant A's Razorpay account can never settle a payment in tenant B's schema.
 *
 * <p>Served under {@code /webhooks}, which the gateway routes without JWT and tenant-common's header
 * filter leaves untenanted; the tenant is bound here, explicitly, once the signature holds.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayGateway razorpayGateway;
    private final PaymentService paymentService;

    @PostMapping("/webhooks/payments/razorpay/{token}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String token,
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // 404 for an unknown token and 401 for a bad signature alike would tell a prober which
        // tokens exist; both are answered the same way.
        return razorpayGateway.forWebhook(token)
                .filter(merchant -> merchant.webhookSignatureMatches(payload, signature))
                .map(merchant -> {
                    TenantContext.runAs(merchant.tenantKey(), () -> paymentService.applyWebhookEvent(payload));
                    return ResponseEntity.ok(Map.<String, Object>of("status", "ok"));
                })
                .orElseGet(() -> {
                    log.warn("Rejected Razorpay webhook: unknown token or bad signature");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "rejected"));
                });
    }
}
