package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationCipher;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.IntegrationView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.SaveIntegrationRequest;
import com.civileng.marketplace.tenant.model.TenantIntegrationEntity;
import com.civileng.marketplace.tenant.repository.TenantIntegrationRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantIntegrationServiceTest {

    private final Map<TenantIntegrationEntity.Key, TenantIntegrationEntity> table = new HashMap<>();
    private final IntegrationCipher cipher =
            new IntegrationCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private TenantIntegrationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantIntegrationRepository repository = mock(TenantIntegrationRepository.class);
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(table.get(inv.getArgument(0))));
        when(repository.existsById(any())).thenAnswer(inv -> table.containsKey(inv.getArgument(0)));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> store(inv.getArgument(0)));
        when(repository.save(any())).thenAnswer(inv -> store(inv.getArgument(0)));
        when(repository.findByIdTenantKeyOrderByIdCapabilityAsc(anyString())).thenAnswer(inv -> {
            List<TenantIntegrationEntity> rows = new ArrayList<>();
            table.values().forEach(row -> {
                if (row.getId().getTenantKey().equals(inv.getArgument(0))) rows.add(row);
            });
            return rows;
        });

        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.existsByTenantKey(anyString()))
                .thenAnswer(inv -> List.of("acme", "bhoomi").contains(inv.getArgument(0)));

        ObjectProvider<AuditPublisher> audit = mock(ObjectProvider.class);
        service = new TenantIntegrationService(repository, tenants, cipher, audit);
    }

    private TenantIntegrationEntity store(TenantIntegrationEntity row) {
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(java.time.LocalDateTime.now());
        }
        table.put(row.getId(), row);
        return row;
    }

    private static SaveIntegrationRequest razorpay(String keySecret, String webhookSecret) {
        Map<String, String> secrets = new HashMap<>();
        if (keySecret != null) secrets.put("keySecret", keySecret);
        if (webhookSecret != null) secrets.put("webhookSecret", webhookSecret);
        return new SaveIntegrationRequest("BYO", "razorpay", true, Map.of("keyId", "rzp_live_acme"), secrets);
    }

    @Test
    void storesSecretsEncryptedAndOnlyEverReturnsMaskedHints() {
        IntegrationView view = service.save("acme", "payment",
                razorpay("acme-key-secret-123456", "acme-webhook-secret-99"), "7");

        TenantIntegrationEntity row = table.get(new TenantIntegrationEntity.Key("acme", "PAYMENT"));
        assertThat(row.getSecretsCiphertext()).startsWith("v1.")
                .doesNotContain("acme-key-secret-123456");
        assertThat(cipher.decrypt(row.getSecretsCiphertext(), "acme", IntegrationCapability.PAYMENT))
                .contains("acme-key-secret-123456");

        assertThat(view.secretHints()).containsEntry("keySecret", "••••3456")
                .containsEntry("webhookSecret", "••••t-99");
        assertThat(view.toString()).doesNotContain("acme-key-secret-123456", "acme-webhook-secret-99");
        assertThat(view.settings()).containsEntry("keyId", "rzp_live_acme");
    }

    @Test
    void paymentGetsAnOpaqueWebhookUrlThatSurvivesLaterSaves() {
        IntegrationView first = service.save("acme", "payment", razorpay("secret-one-aaaa", "wh-one-aaaaaaa"), "7");
        IntegrationView second = service.save("acme", "payment", razorpay(null, null), "7");

        assertThat(first.webhookPath()).matches("/webhooks/payments/razorpay/[0-9a-f]{48}");
        assertThat(second.webhookPath()).isEqualTo(first.webhookPath());
    }

    @Test
    void omittedSecretsKeepTheStoredValue() {
        service.save("acme", "payment", razorpay("original-secret-1111", "original-webhook-22"), "7");
        service.save("acme", "payment", razorpay("rotated-secret-9999", null), "7");

        TenantIntegrationEntity row = table.get(new TenantIntegrationEntity.Key("acme", "PAYMENT"));
        String secrets = cipher.decrypt(row.getSecretsCiphertext(), "acme", IntegrationCapability.PAYMENT);
        assertThat(secrets).contains("rotated-secret-9999").contains("original-webhook-22")
                .doesNotContain("original-secret-1111");
    }

    @Test
    void missingRequiredSecretsAreRejected() {
        assertThatThrownBy(() -> service.save("acme", "payment", razorpay("only-key-secret-1", null), "7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhookSecret");
    }

    @Test
    void paymentSmsAndWhatsappCannotBorrowThePlatformsAccount() {
        for (String capability : List.of("payment", "sms", "whatsapp")) {
            assertThatThrownBy(() -> service.save("acme", capability,
                    new SaveIntegrationRequest("PLATFORM_SHARED", null, true, Map.of(), Map.of()), "7"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("own provider account");
        }
    }

    @Test
    void emailMayRunOnTheSharedPlatformAccountWithTheTenantsSenderName() {
        IntegrationView view = service.save("acme", "email", new SaveIntegrationRequest(
                "PLATFORM_SHARED", null, true, Map.of("fromName", "Acme Builders"), Map.of()), "7");

        assertThat(view.mode()).isEqualTo("PLATFORM_SHARED");
        assertThat(view.settings()).containsEntry("fromName", "Acme Builders");
        assertThat(view.secretHints()).isEmpty();
    }

    @Test
    void theOperatorTenantHasNoIntegrationRows() {
        assertThatThrownBy(() -> service.list("platform"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform's own");
    }

    @Test
    void unknownTenantsAreNotFound() {
        assertThatThrownBy(() -> service.list("ghost")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void listShowsEveryCapabilityIncludingTheUnconfiguredOnes() {
        service.seedDefaults("bhoomi", "7");

        List<IntegrationView> views = service.list("bhoomi");

        assertThat(views).extracting(IntegrationView::capability)
                .containsExactly("payment", "email", "sms", "whatsapp", "ai");
        assertThat(views).filteredOn(IntegrationView::configured)
                .extracting(IntegrationView::capability).containsExactlyInAnyOrder("email", "ai");
    }

    @Test
    void shortSecretsRevealNothingInTheirHint() {
        assertThat(TenantIntegrationService.hints(Map.of("token", "abc123"))).containsEntry("token", "••••");
    }
}
