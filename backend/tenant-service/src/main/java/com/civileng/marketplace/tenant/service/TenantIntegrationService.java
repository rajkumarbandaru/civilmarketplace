package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationCipher;
import com.civileng.marketplace.tenant.common.integration.IntegrationMode;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.CapabilityView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.IntegrationView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.ProviderView;
import com.civileng.marketplace.tenant.dto.IntegrationDtos.SaveIntegrationRequest;
import com.civileng.marketplace.tenant.model.TenantIntegrationEntity;
import com.civileng.marketplace.tenant.repository.TenantIntegrationRepository;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Stores each tenant's provider accounts. Secrets go in encrypted and never come back out: every
 * read returns masked hints, and a save that omits a secret keeps the stored one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantIntegrationService {

    /** The operator tenant runs on the platform's own credentials from config, never on a row. */
    static final String OPERATOR_TENANT = "platform";
    private static final String SOURCE = "tenant-service";
    private static final String ENTITY = "TenantIntegration";
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    /** Capabilities a new tenant starts with on the platform's account; see V5. */
    private static final Set<IntegrationCapability> SHARED_BY_DEFAULT =
            Set.of(IntegrationCapability.EMAIL, IntegrationCapability.AI);

    private final TenantIntegrationRepository repository;
    private final TenantRepository tenantRepository;
    private final IntegrationCipher cipher;
    private final ObjectProvider<AuditPublisher> auditPublisher;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public List<CapabilityView> catalog() {
        return Arrays.stream(IntegrationCapability.values())
                .map(capability -> {
                    Map<String, ProviderView> providers = new LinkedHashMap<>();
                    capability.providers().forEach((key, spec) ->
                            providers.put(key, new ProviderView(spec.settings(), spec.secrets())));
                    return new CapabilityView(capability.key(), capability.allowsPlatformShared(), providers);
                })
                .toList();
    }

    /** Every capability for the tenant, configured or not, so the console can show the gaps. */
    public List<IntegrationView> list(String tenantKey) {
        requireCustomerTenant(tenantKey);
        Map<String, TenantIntegrationEntity> rows = new LinkedHashMap<>();
        repository.findByIdTenantKeyOrderByIdCapabilityAsc(tenantKey)
                .forEach(row -> rows.put(row.getId().getCapability(), row));
        return Arrays.stream(IntegrationCapability.values())
                .map(capability -> view(capability, rows.get(capability.name())))
                .toList();
    }

    @Transactional
    public IntegrationView save(String tenantKey, String capabilityKey, SaveIntegrationRequest request,
                                String actorId) {
        requireCustomerTenant(tenantKey);
        IntegrationCapability capability = IntegrationCapability.fromKey(capabilityKey);
        IntegrationMode mode = parseMode(request.mode());

        TenantIntegrationEntity row = repository
                .findById(new TenantIntegrationEntity.Key(tenantKey, capability.name()))
                .orElseGet(() -> new TenantIntegrationEntity(tenantKey, capability.name()));
        boolean created = row.getCreatedAt() == null;

        if (mode == IntegrationMode.PLATFORM_SHARED) {
            if (!capability.allowsPlatformShared()) {
                throw new IllegalArgumentException(capability.key()
                        + " must use the tenant's own provider account; the platform's cannot be shared");
            }
            row.setMode(mode.name());
            row.setProvider(null);
            row.setSettingsJson(write(clean(request.settings())));
            row.setSecretsCiphertext(null);
            row.setSecretHintsJson(null);
            row.setWebhookToken(null);
        } else {
            IntegrationCapability.ProviderSpec spec = capability.provider(request.provider())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown " + capability.key()
                            + " provider '" + request.provider() + "'; expected one of "
                            + capability.providers().keySet()));

            // Switching provider starts the secrets from empty: Brevo's API key is no use to SMTP.
            Map<String, String> secrets = request.provider().equals(row.getProvider())
                    && IntegrationMode.BYO.name().equals(row.getMode())
                    ? readSecrets(row, capability) : new LinkedHashMap<>();
            clean(request.secrets()).forEach(secrets::put);
            secrets.keySet().retainAll(spec.secrets());

            Map<String, String> settings = clean(request.settings());
            settings.keySet().retainAll(spec.settings());

            requireAll(capability, "setting", spec.settings(), settings);
            requireAll(capability, "secret", spec.secrets(), secrets);

            row.setMode(mode.name());
            row.setProvider(request.provider());
            row.setSettingsJson(write(settings));
            row.setSecretsCiphertext(cipher.encrypt(write(secrets), tenantKey, capability));
            row.setSecretHintsJson(write(hints(secrets)));
            if (capability == IntegrationCapability.PAYMENT && row.getWebhookToken() == null) {
                row.setWebhookToken(newWebhookToken());
            }
        }
        row.setEnabled(request.enabled() == null || request.enabled());
        row.setUpdatedBy(actorId);
        TenantIntegrationEntity saved = repository.saveAndFlush(row);

        audit(created ? AuditAction.CREATE : AuditAction.UPDATE, actorId, tenantKey, capability,
                "mode=" + mode + ", provider=" + saved.getProvider() + ", enabled=" + saved.isEnabled()
                        + ", secretsSet=" + hints(readSecrets(saved, capability)).keySet());
        log.info("Tenant '{}' {} integration saved ({} {}) by {}", tenantKey, capability.key(),
                mode, saved.getProvider(), actorId);
        return view(capability, saved);
    }

    @Transactional
    public void delete(String tenantKey, String capabilityKey, String actorId) {
        requireCustomerTenant(tenantKey);
        IntegrationCapability capability = IntegrationCapability.fromKey(capabilityKey);
        TenantIntegrationEntity.Key id = new TenantIntegrationEntity.Key(tenantKey, capability.name());
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("No " + capability.key() + " integration for " + tenantKey);
        }
        repository.deleteById(id);
        audit(AuditAction.DELETE, actorId, tenantKey, capability, "removed");
        log.info("Tenant '{}' {} integration removed by {}", tenantKey, capability.key(), actorId);
    }

    /** Removes every integration of a tenant being discarded before it ever went live. */
    @Transactional
    public void deleteAll(String tenantKey) {
        repository.deleteAll(repository.findByIdTenantKeyOrderByIdCapabilityAsc(tenantKey));
    }

    /** Called when a tenant is created: mail and AI start on the platform's account. */
    @Transactional
    public void seedDefaults(String tenantKey, String actorId) {
        if (OPERATOR_TENANT.equals(tenantKey)) {
            return;
        }
        for (IntegrationCapability capability : SHARED_BY_DEFAULT) {
            TenantIntegrationEntity row = new TenantIntegrationEntity(tenantKey, capability.name());
            row.setMode(IntegrationMode.PLATFORM_SHARED.name());
            row.setUpdatedBy(actorId);
            repository.save(row);
        }
    }

    private IntegrationView view(IntegrationCapability capability, TenantIntegrationEntity row) {
        if (row == null) {
            return new IntegrationView(capability.key(), false, null, null, false, Map.of(), Map.of(),
                    null, null, null);
        }
        return new IntegrationView(
                capability.key(),
                true,
                row.getMode(),
                row.getProvider(),
                row.isEnabled(),
                read(row.getSettingsJson()),
                read(row.getSecretHintsJson()),
                row.getWebhookToken() == null ? null
                        : "/webhooks/payments/razorpay/" + row.getWebhookToken(),
                row.getUpdatedBy(),
                row.getUpdatedAt());
    }

    private Map<String, String> readSecrets(TenantIntegrationEntity row, IntegrationCapability capability) {
        if (row.getSecretsCiphertext() == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(read(cipher.decrypt(row.getSecretsCiphertext(),
                row.getId().getTenantKey(), capability)));
    }

    /** Masked last-four hints. Short values show nothing at all: four of six characters is a leak. */
    static Map<String, String> hints(Map<String, String> secrets) {
        Map<String, String> hints = new LinkedHashMap<>();
        secrets.forEach((key, value) -> hints.put(key,
                value.length() >= 12 ? "••••" + value.substring(value.length() - 4) : "••••"));
        return hints;
    }

    private void requireCustomerTenant(String tenantKey) {
        if (OPERATOR_TENANT.equals(tenantKey)) {
            throw new IllegalArgumentException("The operator tenant uses the platform's own "
                    + "credentials from configuration; it has no tenant integrations");
        }
        if (!tenantRepository.existsByTenantKey(tenantKey)) {
            throw new NoSuchElementException("Tenant '" + tenantKey + "' not found");
        }
    }

    private static IntegrationMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return IntegrationMode.BYO;
        }
        try {
            return IntegrationMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode must be BYO or PLATFORM_SHARED, not '" + mode + "'");
        }
    }

    private static void requireAll(IntegrationCapability capability, String kind, List<String> required,
                                   Map<String, String> present) {
        List<String> missing = required.stream().filter(key -> !present.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(capability.key() + " is missing " + kind
                    + (missing.size() == 1 ? " " : "s ") + missing);
        }
    }

    private static Map<String, String> clean(Map<String, String> values) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    cleaned.put(key, value.trim());
                }
            });
        }
        return cleaned;
    }

    private String newWebhookToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void audit(AuditAction action, String actorId, String tenantKey,
                       IntegrationCapability capability, String after) {
        AuditPublisher publisher = auditPublisher.getIfAvailable();
        if (publisher == null) {
            return;
        }
        // Filed under the operator tenant: this is a Super Admin action on the platform's registry,
        // and audit-service drops any event that arrives without a tenant header.
        TenantContext.runAs(OPERATOR_TENANT, () -> publisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(parseActor(actorId))
                .actorRole("SUPER_ADMIN")
                .action(action)
                .entityType(ENTITY)
                .entityId(tenantKey + "/" + capability.key())
                .afterState(after)
                .build()));
    }

    private static Long parseActor(String actorId) {
        try {
            return actorId == null ? null : Long.valueOf(actorId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String write(Map<String, String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise integration values", e);
        }
    }

    private Map<String, String> read(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return json.readValue(value, STRING_MAP);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed integration JSON", e);
        }
    }
}
