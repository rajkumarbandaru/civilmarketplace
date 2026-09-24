package com.civileng.marketplace.tenant.common.integration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An external capability a tenant runs through its own provider account.
 *
 * <p>{@link #allowsPlatformShared} is the ownership rule from the architecture spec (06 §9.1):
 * money, DLT-registered SMS and a WhatsApp business number belong to the tenant and can never be
 * borrowed from the platform, whereas mail and AI may run on the platform's account on the tenant's
 * behalf. That rule lives here, next to the providers, so tenant-service (which stores the choice)
 * and every adapter (which honours it) cannot disagree about it.
 */
public enum IntegrationCapability {

    PAYMENT(false, Map.of(
            "razorpay", new ProviderSpec(List.of("keyId"), List.of("keySecret", "webhookSecret")))),

    EMAIL(true, Map.of(
            "smtp", new ProviderSpec(List.of("host", "port", "username", "fromAddress", "fromName"),
                    List.of("password")),
            "brevo", new ProviderSpec(List.of("fromAddress", "fromName"), List.of("apiKey")))),

    SMS(false, Map.of(
            "twilio", new ProviderSpec(List.of("accountSid", "fromNumber", "senderId"),
                    List.of("authToken")))),

    WHATSAPP(false, Map.of(
            "twilio", new ProviderSpec(List.of("accountSid", "fromNumber", "senderName"),
                    List.of("authToken")))),

    AI(true, Map.of(
            "gemini", new ProviderSpec(List.of(), List.of("apiKey"))));

    private final boolean allowsPlatformShared;
    private final Map<String, ProviderSpec> providers;

    IntegrationCapability(boolean allowsPlatformShared, Map<String, ProviderSpec> providers) {
        this.allowsPlatformShared = allowsPlatformShared;
        this.providers = providers;
    }

    public boolean allowsPlatformShared() {
        return allowsPlatformShared;
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
     * from-address); secrets are encrypted at rest and never returned by any API.
     */
    public record ProviderSpec(List<String> settings, List<String> secrets) {

        public Set<String> allKeys() {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(settings);
            keys.addAll(secrets);
            return keys;
        }
    }
}
