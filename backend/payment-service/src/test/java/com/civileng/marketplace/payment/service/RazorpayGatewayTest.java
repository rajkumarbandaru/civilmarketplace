package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationMode;
import com.civileng.marketplace.tenant.common.integration.TenantIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpayGatewayTest {

    private RazorpayGateway gateway;

    @BeforeEach
    void setUp() {
        TenantIntegration acme = new TenantIntegration("acme", IntegrationCapability.PAYMENT,
                IntegrationMode.BYO, "razorpay", true, Map.of("keyId", "rzp_acme"),
                Map.of("keySecret", "acme-secret", "webhookSecret", "acme-wh"), "tok");
        TenantIntegrationStore store = new TenantIntegrationStore() {
            @Override
            public Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability) {
                return "acme".equals(tenantKey) ? Optional.of(acme) : Optional.empty();
            }

            @Override
            public Optional<TenantIntegration> findByWebhookToken(IntegrationCapability c, String t) {
                return Optional.empty();
            }

            @Override
            public void evict(String tenantKey) {
            }
        };
        gateway = new RazorpayGateway(new TenantIntegrationResolver(store, "platform"));
        ReflectionTestUtils.setField(gateway, "platformKeyId", "rzp_platform");
        ReflectionTestUtils.setField(gateway, "platformKeySecret", "platform-secret");
        ReflectionTestUtils.setField(gateway, "platformWebhookSecret", "platform-wh");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void aTenantPaysIntoItsOwnMerchantAccount() {
        TenantContext.set("acme");

        RazorpayGateway.Credentials credentials = gateway.current();

        assertThat(credentials.keyId()).isEqualTo("rzp_acme");
        assertThat(credentials.keySecret()).isEqualTo("acme-secret");
        assertThat(credentials.toString()).doesNotContain("acme-secret");
    }

    @Test
    void aTenantWithoutAMerchantAccountPaysIntoThePlatformsAccount() {
        TenantContext.set("bhoomi");

        RazorpayGateway.Credentials credentials = gateway.current();
        assertThat(credentials.tenantKey()).isEqualTo("bhoomi");
        assertThat(credentials.keyId()).isEqualTo("rzp_platform");
    }

    @Test
    void thePlatformWebhookOnlyServesWorkspacesOnThePlatformAccount() {
        assertThat(gateway.forPlatformWebhook("bhoomi")).get()
                .extracting(RazorpayGateway.Credentials::webhookSecret).isEqualTo("platform-wh");
        assertThat(gateway.forPlatformWebhook("acme")).isEmpty();
        assertThat(gateway.forPlatformWebhook(null)).isEmpty();
    }

    @Test
    void theOperatorTenantUsesThePlatformsAccount() {
        TenantContext.set("platform");

        assertThat(gateway.current().keyId()).isEqualTo("rzp_platform");
    }

    @Test
    void checkoutSignaturesAreVerifiedWithTheTenantsKeySecret() {
        TenantContext.set("acme");
        RazorpayGateway.Credentials credentials = gateway.current();
        String good = RazorpayGateway.Credentials.hmacSha256Hex("order_1|pay_1", "acme-secret");
        String platformSigned = RazorpayGateway.Credentials.hmacSha256Hex("order_1|pay_1", "platform-secret");

        assertThat(credentials.checkoutSignatureMatches("order_1", "pay_1", good)).isTrue();
        assertThat(credentials.checkoutSignatureMatches("order_1", "pay_1", platformSigned)).isFalse();
        assertThat(credentials.checkoutSignatureMatches("order_1", "pay_1", null)).isFalse();
    }
}
