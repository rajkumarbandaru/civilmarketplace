package com.civileng.marketplace.tenant.common.integration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An external capability a tenant runs through a provider account.
 *
 * <p>Every capability defaults to the platform's own account (configured in each service), so a
 * tenant works out of the box. A tenant can bring its own provider account for any of them, or
 * switch one off; see {@link TenantIntegrationResolver}.
 */
public enum IntegrationCapability {

    PAYMENT(Map.of(
            "razorpay", new ProviderSpec(List.of("keyId"), List.of("keySecret", "webhookSecret")))),

    EMAIL(Map.of(
            "smtp", new ProviderSpec(List.of("host", "port", "username", "fromAddress", "fromName"),
                    List.of("password")),
            "brevo", new ProviderSpec(List.of("fromAddress", "fromName"), List.of("apiKey")))),

    SMS(Map.of(
            "twilio", new ProviderSpec(List.of("accountSid", "fromNumber", "senderId"),
                    List.of("authToken")))),

    WHATSAPP(Map.of(
            "twilio", new ProviderSpec(List.of("accountSid", "fromNumber", "senderName"),
                    List.of("authToken")))),

    /**
     * The assistant's model provider; {@code model} is optional and each adapter has a default.
     *
     * <p>{@code openai_compatible} is any server speaking OpenAI's Chat Completions API — open
     * models self-hosted (Ollama, vLLM, LM Studio) or hosted (Groq, OpenRouter, Together, DeepSeek,
     * Mistral). It has no default, so the base URL and model are required; the key is optional
     * because a local server usually has none.
     */
    AI(Map.of(
            "gemini", new ProviderSpec(List.of(), List.of("apiKey"), List.of("model")),
            "openai", new ProviderSpec(List.of(), List.of("apiKey"), List.of("model")),
            "anthropic", new ProviderSpec(List.of(), List.of("apiKey"), List.of("model")),
            "openai_compatible", new ProviderSpec(List.of("baseUrl", "model"), List.of(), List.of(),
                    List.of("apiKey"))));

    private final Map<String, ProviderSpec> providers;

    IntegrationCapability(Map<String, ProviderSpec> providers) {
        this.providers = providers;
    }

    public Map<String, ProviderSpec> providers() {
        return providers;
    }

    public Optional<ProviderSpec> provider(String key) {
        return Optional.ofNullable(key == null ? null : providers.get(key));
    }

    /** Stable lower-case key, used in URLs and the stored row. */
    public String key() {
        return name().toLowerCase();
    }

    public static IntegrationCapability fromKey(String key) {
        for (IntegrationCapability capability : values()) {
            if (capability.key().equalsIgnoreCase(key)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown integration capability '" + key + "'");
    }

    /**
     * What one provider needs. Settings are stored and shown in the clear (an account SID, a
     * from-address); secrets are encrypted at rest and never returned by any API. The optional
     * lists may be left blank.
     */
    public record ProviderSpec(List<String> settings, List<String> secrets, List<String> optionalSettings,
                               List<String> optionalSecrets) {

        public ProviderSpec(List<String> settings, List<String> secrets) {
            this(settings, secrets, List.of(), List.of());
        }

        public ProviderSpec(List<String> settings, List<String> secrets, List<String> optionalSettings) {
            this(settings, secrets, optionalSettings, List.of());
        }

        /** Every secret this provider can store, required or not. */
        public Set<String> allSecrets() {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(secrets);
            keys.addAll(optionalSecrets);
            return keys;
        }

        public Set<String> allKeys() {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(settings);
            keys.addAll(optionalSettings);
            keys.addAll(allSecrets());
            return keys;
        }
    }
}
