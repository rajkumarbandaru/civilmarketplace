package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.ResolvedIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Razorpay, per tenant. Replaces the single process-wide {@code RazorpayClient} bean that took
 * every tenant's payments into whichever merchant account config-repo named.
 *
 * <p>A customer tenant uses the merchant account stored on its own integration row. The operator
 * tenant alone uses the {@code razorpay.*} keys from configuration — the platform's own account,
 * for collecting its own fees. A tenant with no account gets {@code IntegrationNotConfiguredException}
 * (409), never the platform's.
 */
@Component
@RequiredArgsConstructor
public class RazorpayGateway {

    private final TenantIntegrationResolver resolver;

    @Value("${razorpay.key-id:}")
    private String platformKeyId;

    @Value("${razorpay.key-secret:}")
    private String platformKeySecret;

    @Value("${razorpay.webhook-secret:}")
    private String platformWebhookSecret;

    /** The current tenant's merchant credentials. */
    public Credentials current() {
        return from(resolver.require(IntegrationCapability.PAYMENT));
    }

    /**
     * The tenant a webhook URL token belongs to, with that tenant's credentials. Empty for an
     * unknown, disabled or revoked token.
     */
    public Optional<Credentials> forWebhook(String token) {
        return resolver.forWebhook(IntegrationCapability.PAYMENT, token).map(this::from);
    }

    /**
     * For the platform account's own webhook: the tenant named in the order's notes, with the
     * platform's credentials — but only if that tenant really runs on the platform's account, so a
     * tenant with its own merchant account can never be settled through the platform's webhook.
     */
    public Optional<Credentials> forPlatformWebhook(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return Optional.empty();
        }
        return resolver.find(tenantKey, IntegrationCapability.PAYMENT)
                .filter(ResolvedIntegration::usesPlatformCredentials)
                .map(this::from);
    }

    private Credentials from(ResolvedIntegration integration) {
        if (integration.usesPlatformCredentials()) {
            return new Credentials(integration.tenantKey(), platformKeyId, platformKeySecret,
                    platformWebhookSecret);
        }
        return new Credentials(integration.tenantKey(), integration.setting("keyId"),
                integration.secret("keySecret"), integration.secret("webhookSecret"));
    }

    /** One tenant's Razorpay account. {@code toString} omits the secrets. */
    public record Credentials(String tenantKey, String keyId, String keySecret, String webhookSecret) {

        public RazorpayClient client() throws RazorpayException {
            return new RazorpayClient(keyId, keySecret);
        }

        /** Checkout's handler signature: HMAC of {@code order|payment} with the key secret. */
        public boolean checkoutSignatureMatches(String orderId, String paymentId, String signature) {
            return matches(orderId + "|" + paymentId, keySecret, signature);
        }

        /** A webhook body's signature: HMAC of the raw body with the webhook secret. */
        public boolean webhookSignatureMatches(String payload, String signature) {
            return matches(payload, webhookSecret, signature);
        }

        @Override
        public String toString() {
            return "Credentials[" + tenantKey + " keyId=" + keyId + "]";
        }

        /** Constant time: a byte-by-byte equals leaks how much of a forged signature is right. */
        private static boolean matches(String data, String secret, String signature) {
            if (signature == null || secret == null || secret.isBlank() || data == null) {
                return false;
            }
            return MessageDigest.isEqual(
                    hmacSha256Hex(data, secret).getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        }

        static String hmacSha256Hex(String data, String secret) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("HMAC computation failed", e);
            }
        }
    }
}
