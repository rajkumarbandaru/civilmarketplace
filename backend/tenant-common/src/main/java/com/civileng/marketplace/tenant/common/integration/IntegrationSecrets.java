package com.civileng.marketplace.tenant.common.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a tenant integration's secrets are stored: a JSON object of field → sealed value, each field
 * sealed on its own by the {@link SecretsBroker}. Sealing field by field is what lets tenant-service
 * be write-only — replacing one secret keeps the others' sealed values as they are, so it never
 * needs (and its broker policy never grants) the right to open anything.
 *
 * <p>Still reads the previous format — one AES-GCM blob under the shared master key
 * ({@link IntegrationCipher}, {@code v1.…}) — where that key is configured, so existing rows keep
 * working until they are re-sealed.
 */
public class IntegrationSecrets {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP = new TypeReference<>() {
    };

    private final SecretsBroker broker;
    private final IntegrationCipher legacy;

    /** Either may be null, not both: the broker for new secrets, the cipher for reading old ones. */
    public IntegrationSecrets(SecretsBroker broker, IntegrationCipher legacy) {
        if (broker == null && legacy == null) {
            throw new IllegalStateException("Neither a secrets broker (platform.integrations.vault.*) nor a master key "
                    + "(platform.integrations.master-key) is configured: tenant secrets cannot be stored or read");
        }
        this.broker = broker;
        this.legacy = legacy;
    }

    public boolean usesBroker() {
        return broker != null;
    }

    /** Stored in the old single-blob format. */
    public static boolean isLegacy(String stored) {
        return stored != null && stored.startsWith("v1.");
    }

    /** Seals each new value; keeps the stored sealed value of the {@code keep} fields untouched. */
    public String seal(String stored, Map<String, String> fresh, Collection<String> keep, String tenantKey,
                       IntegrationCapability capability) {
        Map<String, String> sealed = new LinkedHashMap<>();
        if (stored != null && !stored.isBlank()) {
            Map<String, String> previous = isLegacy(stored)
                    // Migrating: the old blob has to be opened once to be re-sealed field by field.
                    ? sealAll(legacyOpen(stored, tenantKey, capability), tenantKey, capability)
                    : parse(stored);
            previous.forEach((field, value) -> {
                if (keep.contains(field)) {
                    sealed.put(field, value);
                }
            });
        }
        fresh.forEach((field, value) -> sealed.put(field, sealOne(value, tenantKey, capability)));
        return write(sealed);
    }

    public Map<String, String> open(String stored, String tenantKey, IntegrationCapability capability) {
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        if (isLegacy(stored)) {
            return legacyOpen(stored, tenantKey, capability);
        }
        Map<String, String> plain = new LinkedHashMap<>();
        parse(stored).forEach((field, value) -> plain.put(field, openOne(value, tenantKey, capability)));
        return plain;
    }

    private Map<String, String> sealAll(Map<String, String> plain, String tenantKey, IntegrationCapability capability) {
        Map<String, String> out = new LinkedHashMap<>();
        plain.forEach((k, v) -> out.put(k, sealOne(v, tenantKey, capability)));
        return out;
    }

    private String sealOne(String value, String tenantKey, IntegrationCapability capability) {
        return broker != null ? broker.seal(value, tenantKey, capability) : legacy.encrypt(value, tenantKey, capability);
    }

    private String openOne(String value, String tenantKey, IntegrationCapability capability) {
        if (broker != null && broker.owns(value)) {
            return broker.open(value, tenantKey, capability);
        }
        if (legacy != null && isLegacy(value)) {
            return legacy.decrypt(value, tenantKey, capability);
        }
        throw new IllegalStateException("This service cannot open the " + capability.name().toLowerCase()
                + " secrets of '" + tenantKey + "': no secrets broker configured for them");
    }

    private Map<String, String> legacyOpen(String stored, String tenantKey, IntegrationCapability capability) {
        if (legacy == null) {
            throw new IllegalStateException("Secrets of '" + tenantKey + "' are in the old format and no master key is configured");
        }
        return parse(legacy.decrypt(stored, tenantKey, capability));
    }

    private static Map<String, String> parse(String json) {
        try {
            return JSON.readValue(json, MAP);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable sealed secrets", e);
        }
    }

    private static String write(Map<String, String> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
