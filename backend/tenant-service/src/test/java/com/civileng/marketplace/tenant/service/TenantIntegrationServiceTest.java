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
    private final FakeBroker broker = new FakeBroker();
    private final com.civileng.marketplace.tenant.common.integration.IntegrationSecrets secrets =
            new com.civileng.marketplace.tenant.common.integration.IntegrationSecrets(broker, cipher);
    private TenantRepository tenants;

    /** Seals by tagging; counts opens — tenant-service must never open. */
    static class FakeBroker implements com.civileng.marketplace.tenant.common.integration.SecretsBroker {
        int opens;
        final java.util.Set<String> destroyed = new java.util.HashSet<>();

        @Override
        public String seal(String plaintext, String tenantKey, IntegrationCapability capability) {
            return "vault:v1:" + Base64.getEncoder().encodeToString((tenantKey + "|" + capability + "|" + plaintext).getBytes());
        }

        @Override
        public String open(String sealed, String tenantKey, IntegrationCapability capability) {
            opens++;
            String[] p = new String(Base64.getDecoder().decode(sealed.substring(9))).split("\\|", 3);
            if (destroyed.contains(tenantKey)) throw new com.civileng.marketplace.tenant.common.integration.SecretsDestroyedException(tenantKey, capability);
            return p[2];
        }

        @Override
        public int shred(String tenantKey) {
            destroyed.add(tenantKey);
            return 2;
        }

        @Override
        public boolean owns(String sealed) {
            return sealed.startsWith("vault:");
        }
    }

    private Map<String, String> opened(String capability) {
        TenantIntegrationEntity row = table.get(new TenantIntegrationEntity.Key("acme", capability));
        int before = broker.opens;
        Map<String, String> m = secrets.open(row.getSecretsCiphertext(), "acme", IntegrationCapability.valueOf(capability));
        broker.opens = before;   // the test's own read does not count against the service
        return m;
    }

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

        tenants = mock(TenantRepository.class);
        when(tenants.existsByTenantKey(anyString()))
                .thenAnswer(inv -> List.of("acme", "bhoomi").contains(inv.getArgument(0)));

        ObjectProvider<AuditPublisher> audit = mock(ObjectProvider.class);
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(table.values()));
        ObjectProvider<com.civileng.marketplace.tenant.common.integration.SecretsBroker> brokers = mock(ObjectProvider.class);
        when(brokers.getIfAvailable()).thenReturn(broker);
        service = new TenantIntegrationService(repository, tenants, secrets, brokers, tenants, audit);
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
        assertThat(row.getSecretsCiphertext()).startsWith("{\"keySecret\":\"vault:v1:")
                .doesNotContain("acme-key-secret-123456");
        assertThat(opened("PAYMENT")).containsEntry("keySecret", "acme-key-secret-123456");
        assertThat(broker.opens).as("tenant-service is write-only").isZero();

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

        assertThat(opened("PAYMENT")).containsEntry("keySecret", "rotated-secret-9999")
                .containsEntry("webhookSecret", "original-webhook-22");
        assertThat(broker.opens).as("keeping a secret does not open it").isZero();
    }

    @Test
    void missingRequiredSecretsAreRejected() {
        assertThatThrownBy(() -> service.save("acme", "payment", razorpay("only-key-secret-1", null), "7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhookSecret");
    }

    @Test
    void everyCapabilityMayRunOnThePlatformsAccount() {
        for (String capability : List.of("payment", "sms", "whatsapp", "email", "ai")) {
            IntegrationView view = service.save("acme", capability,
                    new SaveIntegrationRequest("PLATFORM_SHARED", null, true, Map.of(), Map.of()), "7");
            assertThat(view.mode()).isEqualTo("PLATFORM_SHARED");
        }
    }

    @Test
    void anAiProviderKeepsItsOptionalModelAndDropsUnknownSettings() {
        IntegrationView view = service.save("acme", "ai", new SaveIntegrationRequest("BYO", "anthropic", true,
                Map.of("model", "claude-sonnet-5", "colour", "red"), Map.of("apiKey", "sk-ant-0123456789abcd")), "7");

        assertThat(view.provider()).isEqualTo("anthropic");
        assertThat(view.settings()).containsExactly(Map.entry("model", "claude-sonnet-5"));
        assertThat(view.secretHints()).containsEntry("apiKey", "••••abcd");
    }

    @Test
    void anAiProviderNeedsNoModel() {
        IntegrationView view = service.save("acme", "ai", new SaveIntegrationRequest("BYO", "openai", true,
                Map.of(), Map.of("apiKey", "sk-openai-0123456789")), "7");

        assertThat(view.provider()).isEqualTo("openai");
        assertThat(view.settings()).isEmpty();
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

    @Test
    void aSecretSealedUnderTheOldSharedKeyIsResealedPerFieldOnTheNextSave() {
        service.save("acme", "payment", razorpay("original-secret-1111", "original-webhook-22"), "7");
        TenantIntegrationEntity row = table.get(new TenantIntegrationEntity.Key("acme", "PAYMENT"));
        row.setSecretsCiphertext(cipher.encrypt("{\"keySecret\":\"legacy-secret-5555\",\"webhookSecret\":\"legacy-webhook-66\"}",
                "acme", IntegrationCapability.PAYMENT));
        service.save("acme", "payment", razorpay(null, "new-webhook-777777"), "7");
        assertThat(row.getSecretsCiphertext()).startsWith("{").doesNotContain("v1.");
        assertThat(opened("PAYMENT")).containsEntry("keySecret", "legacy-secret-5555")
                .containsEntry("webhookSecret", "new-webhook-777777");
    }

    @Test
    void anArchivedTenantsKeysCanBeDestroyedLeavingItsSecretsUnreadable() {
        service.save("acme", "payment", razorpay("acme-key-secret-123456", "acme-webhook-secret-99"), "7");
        com.civileng.marketplace.tenant.model.Tenant acme = com.civileng.marketplace.tenant.model.Tenant.builder()
                .tenantKey("acme").status(com.civileng.marketplace.tenant.model.TenantStatus.ACTIVE).build();
        when(tenants.findByTenantKey("acme")).thenReturn(Optional.of(acme));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cryptoShred("acme", "acme", "7"))
                .hasMessageContaining("archived");
        acme.setStatus(com.civileng.marketplace.tenant.model.TenantStatus.ARCHIVED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cryptoShred("acme", "acm", "7"))
                .hasMessageContaining("confirm");

        TenantIntegrationService.CryptoShredResult r = service.cryptoShred("acme", "acme", "7");
        assertThat(r.keysDestroyed()).isEqualTo(2);
        assertThat(r.integrationsDisabled()).isEqualTo(1);
        assertThat(acme.getKeysDestroyedAt()).isNotNull();
        assertThat(table.get(new TenantIntegrationEntity.Key("acme", "PAYMENT")).isEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> opened("PAYMENT"))
                .isInstanceOf(com.civileng.marketplace.tenant.common.integration.SecretsDestroyedException.class);
    }

    @Test
    void secretsUnderTheOldSharedKeyAreMovedIntoTheBrokerAtStartup() {
        service.save("acme", "payment", razorpay("original-secret-1111", "original-webhook-22"), "7");
        TenantIntegrationEntity row = table.get(new TenantIntegrationEntity.Key("acme", "PAYMENT"));
        row.setSecretsCiphertext(cipher.encrypt("{\"keySecret\":\"legacy-secret-5555\",\"webhookSecret\":\"legacy-webhook-66\"}",
                "acme", IntegrationCapability.PAYMENT));
        assertThat(service.resealLegacySecrets()).isEqualTo(1);
        assertThat(row.getSecretsCiphertext()).startsWith("{").contains("vault:v1:");
        assertThat(opened("PAYMENT")).containsEntry("keySecret", "legacy-secret-5555")
                .containsEntry("webhookSecret", "legacy-webhook-66");
        assertThat(broker.opens).isZero();
        assertThat(service.resealLegacySecrets()).isZero();   // idempotent
    }
}
