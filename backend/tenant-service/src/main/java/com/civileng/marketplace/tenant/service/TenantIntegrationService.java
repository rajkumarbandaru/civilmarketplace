package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationSecrets;
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
    /**
     * Rows a new tenant starts with; see V5. Only a record of the choice: a tenant with no row at
     * all runs on the platform's account too (see TenantIntegrationResolver).
     */
    private static final Set<IntegrationCapability> SHARED_BY_DEFAULT =
            Set.of(IntegrationCapability.EMAIL, IntegrationCapability.AI);

    private final TenantIntegrationRepository repository;
    private final TenantRepository tenantRepository;
    private final IntegrationSecrets secrets;
    private final org.springframework.beans.factory.ObjectProvider<com.civileng.marketplace.tenant.common.integration.SecretsBroker> brokers;
    private final com.civileng.marketplace.tenant.repository.TenantRepository tenants;
    private final ObjectProvider<AuditPublisher> auditPublisher;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public List<CapabilityView> catalog() {
        return Arrays.stream(IntegrationCapability.values())
                .map(capability -> {
                    Map<String, ProviderView> providers = new LinkedHashMap<>();
                    capability.providers().forEach((key, spec) ->
                            providers.put(key, new ProviderView(spec.settings(), spec.secrets(),
                                    spec.optionalSettings())));
                    return new CapabilityView(capability.key(), providers);
                })
                .toList();
    }

    /** Every capability for the tenant; one with no row runs on the platform's account. */
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
            boolean sameAccount = request.provider().equals(row.getProvider())
                    && IntegrationMode.BYO.name().equals(row.getMode());
            // Write-only: a secret left blank keeps its sealed value as it is. Which ones are set is
            // known from their hints — this service never opens a secret (its broker policy cannot).
            Map<String, String> previousHints = sameAccount ? read(row.getSecretHintsJson()) : new LinkedHashMap<>();
            Map<String, String> fresh = clean(request.secrets());
            fresh.keySet().retainAll(spec.secrets());
            java.util.Set<String> keep = new java.util.LinkedHashSet<>(previousHints.keySet());
            keep.removeAll(fresh.keySet());
            keep.retainAll(spec.secrets());
            Map<String, String> present = new LinkedHashMap<>(fresh);
            keep.forEach(k -> present.put(k, "(kept)"));

            Map<String, String> settings = clean(request.settings());
            java.util.Set<String> allowedSettings = new java.util.HashSet<>(spec.settings());
            allowedSettings.addAll(spec.optionalSettings());
            settings.keySet().retainAll(allowedSettings);

            requireAll(capability, "setting", spec.settings(), settings);
            requireAll(capability, "secret", spec.secrets(), present);

            row.setMode(mode.name());
            row.setProvider(request.provider());
            row.setSettingsJson(write(settings));
            row.setSecretsCiphertext(secrets.seal(sameAccount ? row.getSecretsCiphertext() : null, fresh, keep,
                    tenantKey, capability));
            Map<String, String> newHints = new LinkedHashMap<>();
            keep.forEach(k -> newHints.put(k, previousHints.get(k)));
            newHints.putAll(hints(fresh));
            row.setSecretHintsJson(write(newHints));
            if (capability == IntegrationCapability.PAYMENT && row.getWebhookToken() == null) {
                row.setWebhookToken(newWebhookToken());
            }
        }
        row.setEnabled(request.enabled() == null || request.enabled());
        row.setUpdatedBy(actorId);
        TenantIntegrationEntity saved = repository.saveAndFlush(row);

        audit(created ? AuditAction.CREATE : AuditAction.UPDATE, actorId, tenantKey, capability,
                "mode=" + mode + ", provider=" + saved.getProvider() + ", enabled=" + saved.isEnabled()
                        + ", secretsSet=" + read(saved.getSecretHintsJson()).keySet());
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
                .entityId(capability == null ? tenantKey : tenantKey + "/" + capability.key())
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

    /**
     * Moves secrets stored under the old shared key into the broker, field by field — the one time
     * tenant-service opens a secret (with the old key it still holds; never through the broker).
     * Runs at startup; afterwards the services that use secrets need only their broker tokens.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.transaction.annotation.Transactional
    public int resealLegacySecrets() {
        if (!secrets.usesBroker()) {
            return 0;
        }
        int moved = 0;
        for (TenantIntegrationEntity row : repository.findAll()) {
            if (!IntegrationSecrets.isLegacy(row.getSecretsCiphertext())) {
                continue;
            }
            IntegrationCapability capability = IntegrationCapability.valueOf(row.getId().getCapability());
            try {
                row.setSecretsCiphertext(secrets.seal(row.getSecretsCiphertext(), Map.of(),
                        read(row.getSecretHintsJson()).keySet(), row.getId().getTenantKey(), capability));
                repository.save(row);
                moved++;
            } catch (RuntimeException e) {
                log.error("Could not re-seal {} secrets of '{}'", capability, row.getId().getTenantKey(), e);
            }
        }
        if (moved > 0) {
            log.info("Re-sealed {} tenant integration(s) into the secrets broker", moved);
        }
        return moved;
    }

    /**
     * Crypto-shredding an archived tenant: its keys are destroyed in the secrets broker, so every
     * provider credential sealed under them — here and in every backup — can never be read again.
     * Irreversible; the operator must type the tenant key to confirm.
     */
    @org.springframework.transaction.annotation.Transactional
    public CryptoShredResult cryptoShred(String tenantKey, String confirmation, String actorId) {
        requireCustomerTenant(tenantKey);
        com.civileng.marketplace.tenant.model.Tenant tenant = tenants.findByTenantKey(tenantKey)
                .orElseThrow(() -> new java.util.NoSuchElementException("No tenant " + tenantKey));
        if (tenant.getStatus() != com.civileng.marketplace.tenant.model.TenantStatus.ARCHIVED) {
            throw new IllegalStateException("Only an archived tenant's keys can be destroyed");
        }
        if (!tenantKey.equals(confirmation)) {
            throw new IllegalArgumentException("Type the tenant key to confirm");
        }
        com.civileng.marketplace.tenant.common.integration.SecretsBroker broker = brokers.getIfAvailable();
        if (broker == null) {
            throw new IllegalStateException("No secrets broker is configured; there are no per-tenant keys to destroy");
        }
        int destroyed = broker.shred(tenantKey);
        int disabled = 0;
        for (TenantIntegrationEntity row : repository.findAll()) {
            if (row.getId().getTenantKey().equals(tenantKey) && row.getSecretsCiphertext() != null) {
                row.setEnabled(false);
                repository.save(row);
                disabled++;
            }
        }
        tenant.setKeysDestroyedAt(java.time.LocalDateTime.now());
        tenants.save(tenant);
        audit(AuditAction.DELETE, actorId, tenantKey, null, "crypto-shred: " + destroyed + " key(s) destroyed, "
                + disabled + " integration(s) disabled");
        log.warn("Tenant '{}' crypto-shredded by {}: {} key(s) destroyed", tenantKey, actorId, destroyed);
        return new CryptoShredResult(tenantKey, destroyed, disabled, tenant.getKeysDestroyedAt());
    }

    public record CryptoShredResult(String tenantKey, int keysDestroyed, int integrationsDisabled,
                                    java.time.LocalDateTime destroyedAt) { }
}
