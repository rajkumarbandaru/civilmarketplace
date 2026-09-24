package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.service.PaymentService;
import com.civileng.marketplace.payment.service.RazorpayGateway;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationMode;
import com.civileng.marketplace.tenant.common.integration.TenantIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RazorpayWebhookControllerTest {

    private static final String BODY = "{\"event\":\"payment.captured\"}";

    private final PaymentService paymentService = mock(PaymentService.class);
    private final AtomicReference<String> tenantSeenByService = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantIntegrationStore store = new ListStore(List.of(
                merchant("acme", "tok-acme", "acme-webhook-secret"),
                merchant("bhoomi", "tok-bhoomi", "bhoomi-webhook-secret")));
        RazorpayGateway gateway = new RazorpayGateway(new TenantIntegrationResolver(store, "platform"));
        doAnswer(inv -> {
            tenantSeenByService.set(TenantContext.get());
            return null;
        }).when(paymentService).applyWebhookEvent(anyString());

        mvc = MockMvcBuilders.standaloneSetup(new RazorpayWebhookController(gateway, paymentService)).build();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void aCorrectlySignedWebhookIsAppliedInsideItsOwnTenant() throws Exception {
        mvc.perform(post("/webhooks/payments/razorpay/tok-acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Razorpay-Signature", sign(BODY, "acme-webhook-secret")))
                .andExpect(status().isOk());

        assertThat(tenantSeenByService.get()).isEqualTo("acme");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void anotherTenantsSecretCannotSignForThisTenant() throws Exception {
        mvc.perform(post("/webhooks/payments/razorpay/tok-acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Razorpay-Signature", sign(BODY, "bhoomi-webhook-secret")))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).applyWebhookEvent(anyString());
    }

    @Test
    void unknownTokensAndMissingSignaturesAreRejectedAlike() throws Exception {
        mvc.perform(post("/webhooks/payments/razorpay/tok-nobody")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Razorpay-Signature", sign(BODY, "acme-webhook-secret")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/payments/razorpay/tok-acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).applyWebhookEvent(anyString());
    }

    private static TenantIntegration merchant(String tenant, String token, String webhookSecret) {
        return new TenantIntegration(tenant, IntegrationCapability.PAYMENT, IntegrationMode.BYO, "razorpay",
                true, Map.of("keyId", "rzp_" + tenant),
                Map.of("keySecret", tenant + "-key-secret", "webhookSecret", webhookSecret), token);
    }

    static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** In-memory stand-in for tenant-service's table. */
    static final class ListStore implements TenantIntegrationStore {
        private final List<TenantIntegration> rows;

        ListStore(List<TenantIntegration> rows) {
            this.rows = rows;
        }

        @Override
        public Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability) {
            return rows.stream().filter(r -> r.tenantKey().equals(tenantKey) && r.capability() == capability)
                    .findFirst();
        }

        @Override
        public Optional<TenantIntegration> findByWebhookToken(IntegrationCapability capability, String token) {
            return rows.stream().filter(r -> r.capability() == capability && token.equals(r.webhookToken()))
                    .findFirst();
        }

        @Override
        public void evict(String tenantKey) {
        }
    }
}
