package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.dto.AiChatRequest;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationMode;
import com.civileng.marketplace.tenant.common.integration.TenantIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistantRoutingTest {

    /** Records what it was asked with instead of calling anyone. */
    static final class FakeModel implements ChatModel {
        final String provider;
        String platformKey;
        final List<String> calls = new ArrayList<>();

        FakeModel(String provider, String platformKey) {
            this.provider = provider;
            this.platformKey = platformKey;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public String platformKey() {
            return platformKey;
        }

        @Override
        public String ask(String apiKey, String model, String instruction, List<AiChatRequest.Turn> history,
                          String message) {
            calls.add(apiKey + "|" + model + "|" + history.size() + "|" + message);
            return provider + " says hi";
        }
    }

    private final FakeModel gemini = new FakeModel("gemini", "platform-gemini-key");
    private final FakeModel openai = new FakeModel("openai", "");
    private final FakeModel anthropic = new FakeModel("anthropic", "");
    private AiAssistant assistant;

    @BeforeEach
    void setUp() {
        List<TenantIntegration> rows = List.of(
                new TenantIntegration("acme", IntegrationCapability.AI, IntegrationMode.BYO, "gemini", true,
                        Map.of(), Map.of("apiKey", "acme-gemini-key"), null),
                new TenantIntegration("claude-co", IntegrationCapability.AI, IntegrationMode.BYO, "anthropic", true,
                        Map.of("model", "claude-sonnet-5"), Map.of("apiKey", "sk-ant-key"), null),
                new TenantIntegration("gpt-co", IntegrationCapability.AI, IntegrationMode.BYO, "openai", true,
                        Map.of(), Map.of("apiKey", "sk-openai-key"), null),
                new TenantIntegration("shared", IntegrationCapability.AI, IntegrationMode.PLATFORM_SHARED, null,
                        true, Map.of(), Map.of(), null),
                new TenantIntegration("off", IntegrationCapability.AI, IntegrationMode.PLATFORM_SHARED, null,
                        false, Map.of(), Map.of(), null));
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
        assistant = new AiAssistant(new TenantIntegrationResolver(store, "platform"), List.of(gemini, openai, anthropic));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void aWorkspaceWithItsOwnGeminiKeyUsesIt() {
        TenantContext.set("acme");
        assistant.ask("hello", List.of(), null);
        assertThat(gemini.calls).containsExactly("acme-gemini-key|null|0|hello");
    }

    @Test
    void aWorkspaceCanChooseAnotherProviderAndModel() {
        TenantContext.set("claude-co");
        assertThat(assistant.provider()).isEqualTo("anthropic");
        assertThat(assistant.ask("hello", List.of(), null)).isEqualTo("anthropic says hi");
        assertThat(anthropic.calls).containsExactly("sk-ant-key|claude-sonnet-5|0|hello");

        TenantContext.set("gpt-co");
        assertThat(assistant.ask("hello", List.of(), null)).isEqualTo("openai says hi");
        assertThat(openai.calls).containsExactly("sk-openai-key|null|0|hello");
        assertThat(gemini.calls).isEmpty();
    }

    @Test
    void sharedWorkspacesNewWorkspacesAndTheOperatorUseThePlatformsGemini() {
        for (String tenant : List.of("shared", "brand-new", "platform")) {
            TenantContext.set(tenant);
            assertThat(assistant.provider()).isEqualTo("gemini");
            assistant.ask("q", List.of(), "rates");
        }
        assertThat(gemini.calls).hasSize(3).allMatch(c -> c.startsWith("platform-gemini-key|null|"));
    }

    @Test
    void aWorkspaceThatSwitchedTheAssistantOffHasNone() {
        TenantContext.set("off");

        assertThat(assistant.isConfigured()).isFalse();
        assertThat(assistant.ask("hello", List.of(), null)).isNull();
    }

    @Test
    void withoutAPlatformKeyTheDefaultAssistantIsUnavailable() {
        gemini.platformKey = "";
        TenantContext.set("shared");

        assertThat(assistant.isConfigured()).isFalse();
    }

    @Test
    void onlyTheRecentNonEmptyTurnsAreForwarded() {
        List<AiChatRequest.Turn> history = new ArrayList<>();
        IntStream.range(0, 20).forEach(i -> history.add(turn("user", "q" + i)));
        history.add(turn("user", " "));
        history.add(null);

        List<AiChatRequest.Turn> recent = AiAssistant.recent(history);

        assertThat(recent).hasSize(AiAssistant.MAX_HISTORY_TURNS);
        assertThat(recent.get(recent.size() - 1).getText()).isEqualTo("q19");
    }

    private static AiChatRequest.Turn turn(String role, String text) {
        AiChatRequest.Turn t = new AiChatRequest.Turn();
        t.setRole(role);
        t.setText(text);
        return t;
    }
}
