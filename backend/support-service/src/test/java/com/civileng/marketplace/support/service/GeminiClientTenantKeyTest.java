package com.civileng.marketplace.support.service;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiClientTenantKeyTest {

    private GeminiClient gemini;

    @BeforeEach
    void setUp() {
        List<TenantIntegration> rows = List.of(
                new TenantIntegration("acme", IntegrationCapability.AI, IntegrationMode.BYO, "gemini", true,
                        Map.of(), Map.of("apiKey", "acme-gemini-key"), null),
                new TenantIntegration("shared", IntegrationCapability.AI, IntegrationMode.PLATFORM_SHARED, null,
                        true, Map.of(), Map.of(), null));
        TenantIntegrationStore store = new TenantIntegrationStore() {
            @Override
            public Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability) {
                return rows.stream().filter(r -> r.tenantKey().equals(tenantKey)).findFirst();
            }

            @Override
            public Optional<TenantIntegration> findByWebhookToken(IntegrationCapability c, String t) {
                return Optional.empty();
            }

            @Override
            public void evict(String tenantKey) {
            }
        };
        gemini = new GeminiClient(new TenantIntegrationResolver(store, "platform"));
        ReflectionTestUtils.setField(gemini, "apiKey", "platform-gemini-key");
        ReflectionTestUtils.setField(gemini, "enabled", true);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void usesTheTenantsOwnKey() {
        TenantContext.set("acme");
        assertThat(ReflectionTestUtils.<String>invokeMethod(gemini, "currentKey")).isEqualTo("acme-gemini-key");
    }

    @Test
    void sharedTenantsAndTheOperatorUseThePlatformKey() {
        TenantContext.set("shared");
        assertThat(ReflectionTestUtils.<String>invokeMethod(gemini, "currentKey")).isEqualTo("platform-gemini-key");
        TenantContext.set("platform");
        assertThat(ReflectionTestUtils.<String>invokeMethod(gemini, "currentKey")).isEqualTo("platform-gemini-key");
    }

    @Test
    void aTenantWithNoAiIntegrationHasNoAssistant() {
        TenantContext.set("bhoomi");

        assertThat(gemini.isConfigured()).isFalse();
        assertThat(gemini.ask("hello", List.of(), null)).isNull();
    }
}
