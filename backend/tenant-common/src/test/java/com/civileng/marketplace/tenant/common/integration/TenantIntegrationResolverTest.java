package com.civileng.marketplace.tenant.common.integration;

import com.civileng.marketplace.tenant.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIntegrationResolverTest {

    private final FakeStore store = new FakeStore();
    private final TenantIntegrationResolver resolver = new TenantIntegrationResolver(store, "platform");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void tenantOwnedCredentialsComeFromTheCurrentTenantsRow() {
        store.put(row("acme", IntegrationCapability.PAYMENT, IntegrationMode.BYO,
                Map.of("keyId", "rzp_acme"), Map.of("keySecret", "acme-secret"), true));
        store.put(row("bhoomi", IntegrationCapability.PAYMENT, IntegrationMode.BYO,
                Map.of("keyId", "rzp_bhoomi"), Map.of("keySecret", "bhoomi-secret"), true));

        TenantContext.set("acme");
        ResolvedIntegration resolved = resolver.require(IntegrationCapability.PAYMENT);

        assertThat(resolved.source()).isEqualTo(ResolvedIntegration.Source.TENANT);
        assertThat(resolved.setting("keyId")).isEqualTo("rzp_acme");
        assertThat(resolved.secret("keySecret")).isEqualTo("acme-secret");
    }

    @Test
    void aTenantWithNothingConfiguredRunsOnThePlatformDefault() {
        TenantContext.set("acme");

        for (IntegrationCapability capability : IntegrationCapability.values()) {
            ResolvedIntegration resolved = resolver.require(capability);
            assertThat(resolved.source()).isEqualTo(ResolvedIntegration.Source.PLATFORM_SHARED);
            assertThat(resolved.tenantKey()).isEqualTo("acme");
            assertThat(resolved.secrets()).isEmpty();
        }
    }

    @Test
    void theOperatorTenantResolvesToThePlatformsOwnCredentials() {
        TenantContext.set("platform");

        ResolvedIntegration resolved = resolver.require(IntegrationCapability.PAYMENT);

        assertThat(resolved.source()).isEqualTo(ResolvedIntegration.Source.PLATFORM);
        assertThat(resolved.usesPlatformCredentials()).isTrue();
    }

    @Test
    void sharedModeUsesPlatformCredentialsWithTheTenantsSettings() {
        store.put(row("acme", IntegrationCapability.EMAIL, IntegrationMode.PLATFORM_SHARED,
                Map.of("fromName", "Acme Builders"), Map.of(), true));
        TenantContext.set("acme");

        ResolvedIntegration resolved = resolver.require(IntegrationCapability.EMAIL);

        assertThat(resolved.source()).isEqualTo(ResolvedIntegration.Source.PLATFORM_SHARED);
        assertThat(resolved.setting("fromName")).isEqualTo("Acme Builders");
        assertThat(resolved.secrets()).isEmpty();
    }

    @Test
    void sharedModeIsAllowedForEveryCapability() {
        store.put(row("acme", IntegrationCapability.PAYMENT, IntegrationMode.PLATFORM_SHARED,
                Map.of(), Map.of(), true));
        TenantContext.set("acme");

        assertThat(resolver.require(IntegrationCapability.PAYMENT).usesPlatformCredentials()).isTrue();
    }

    @Test
    void aSwitchedOffCapabilityIsNotConfiguredEvenThoughThePlatformHasOne() {
        store.put(row("acme", IntegrationCapability.WHATSAPP, IntegrationMode.PLATFORM_SHARED,
                Map.of(), Map.of(), false));
        TenantContext.set("acme");

        assertThatThrownBy(() -> resolver.require(IntegrationCapability.WHATSAPP))
                .isInstanceOf(IntegrationNotConfiguredException.class)
                .hasMessageContaining("whatsapp");
    }

    @Test
    void aiProvidersTakeAnOptionalModel() {
        assertThat(IntegrationCapability.AI.providers()).containsOnlyKeys("gemini", "openai", "anthropic");
        IntegrationCapability.ProviderSpec openai = IntegrationCapability.AI.provider("openai").orElseThrow();
        assertThat(openai.settings()).isEmpty();
        assertThat(openai.optionalSettings()).containsExactly("model");
        assertThat(openai.allKeys()).containsExactly("model", "apiKey");
    }

    @Test
    void disabledRowsAreNotConfigured() {
        store.put(row("acme", IntegrationCapability.SMS, IntegrationMode.BYO,
                Map.of("accountSid", "AC1"), Map.of("authToken", "t"), false));
        TenantContext.set("acme");

        assertThat(resolver.find(IntegrationCapability.SMS)).isEmpty();
    }

    @Test
    void requiresABoundTenant() {
        assertThatThrownBy(() -> resolver.require(IntegrationCapability.EMAIL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void webhookTokenResolvesToItsOwnTenantOnly() {
        TenantIntegration acme = new TenantIntegration("acme", IntegrationCapability.PAYMENT,
                IntegrationMode.BYO, "razorpay", true, Map.of(), Map.of("webhookSecret", "wa"), "tok-acme");
        store.put(acme);

        assertThat(resolver.forWebhook(IntegrationCapability.PAYMENT, "tok-acme"))
                .get().extracting(ResolvedIntegration::tenantKey).isEqualTo("acme");
        assertThat(resolver.forWebhook(IntegrationCapability.PAYMENT, "tok-unknown")).isEmpty();
        assertThat(resolver.forWebhook(IntegrationCapability.SMS, "tok-acme")).isEmpty();
    }

    private static TenantIntegration row(String tenant, IntegrationCapability capability,
                                         IntegrationMode mode, Map<String, String> settings,
                                         Map<String, String> secrets, boolean enabled) {
        return new TenantIntegration(tenant, capability, mode, "provider", enabled, settings, secrets, null);
    }

    private static final class FakeStore implements TenantIntegrationStore {
        private final Map<String, TenantIntegration> rows = new HashMap<>();

        void put(TenantIntegration row) {
            rows.put(row.tenantKey() + "|" + row.capability(), row);
        }

        @Override
        public Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability) {
            return Optional.ofNullable(rows.get(tenantKey + "|" + capability));
        }

        @Override
        public Optional<TenantIntegration> findByWebhookToken(IntegrationCapability capability, String token) {
            return rows.values().stream()
                    .filter(r -> r.capability() == capability && token.equals(r.webhookToken()))
                    .findFirst();
        }

        @Override
        public void evict(String tenantKey) {
        }
    }
}
