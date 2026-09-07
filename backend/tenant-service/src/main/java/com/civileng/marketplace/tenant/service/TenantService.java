package com.civileng.marketplace.tenant.service;

import com.civileng.marketplace.tenant.common.TenantBranding;
import com.civileng.marketplace.tenant.common.TenantEventMessage;
import com.civileng.marketplace.tenant.common.TenantKey;
import com.civileng.marketplace.tenant.common.TenantTopics;
import com.civileng.marketplace.tenant.common.TenantMenuOverride;
import com.civileng.marketplace.tenant.dto.CreateTenantRequest;
import com.civileng.marketplace.tenant.dto.UpdateTenantRequest;
import com.civileng.marketplace.tenant.model.TenantMenuOverrideEntity;
import com.civileng.marketplace.tenant.repository.TenantMenuOverrideRepository;
import com.civileng.marketplace.tenant.model.PlatformModule;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.model.Vertical;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMenuOverrideRepository menuOverrideRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Tenant create(CreateTenantRequest request, String actorId) {
        String key = TenantKey.normalise(request.getTenantKey());

        if (tenantRepository.existsByTenantKey(key)) {
            throw new IllegalArgumentException("Tenant '" + key + "' already exists");
        }

        String subdomain = request.getSubdomain() == null || request.getSubdomain().isBlank()
                ? key
                : TenantKey.normalise(request.getSubdomain());
        if (tenantRepository.existsBySubdomain(subdomain)) {
            throw new IllegalArgumentException("Subdomain '" + subdomain + "' is taken");
        }

        String customDomain = normaliseDomain(request.getCustomDomain());
        if (customDomain != null && tenantRepository.findByCustomDomain(customDomain).isPresent()) {
            throw new IllegalArgumentException("Domain '" + customDomain + "' is already in use");
        }

        Vertical vertical = request.getVertical() == null
                ? Vertical.CIVIL_MARKETPLACE
                : request.getVertical();

        // Validated before the row is written, so a bad colour or an unknown UI style is a 400 on
        // the create call rather than a tenant that exists with branding nothing can render.
        TenantBranding branding = request.getBranding();
        if (branding != null) {
            branding.validate();
        }

        Tenant tenant = Tenant.builder()
                .tenantKey(key)
                .name(request.getName())
                .subdomain(subdomain)
                .customDomain(customDomain)
                .status(TenantStatus.ACTIVE)
                .contactEmail(request.getContactEmail())
                .plan(request.getPlan() == null ? "STANDARD" : request.getPlan())
                .vertical(vertical)
                .enabledModules(resolveModules(vertical, request.getModules()))
                .createdBy(actorId)
                .landingPath(blankToNull(request.getLandingPath()))
                .build();
        tenant.applyBranding(branding);
        tenant = tenantRepository.save(tenant);

        List<TenantMenuOverride> overrides = replaceOverrides(key, request.getMenuOverrides());

        publishAfterCommit(tenant, branding, overrides);
        log.info("Tenant '{}' created ({} vertical) by {}", key, vertical, actorId);
        return tenant;
    }

    /**
     * Changes a tenant's identity: name, subdomain, custom domain, contact, plan, vertical.
     *
     * <p>Deliberately not touching modules, menu or branding even though the console shows all four
     * on one screen. Each of those has its own consequence to explain — removing a module 404s a
     * live user, replacing branding overwrites a theme the tenant chose — and folding them into one
     * save would mean one confirmation dialog trying to describe all of it at once.
     *
     * <p>Both uniqueness checks exclude this tenant, so re-saving the form without touching the
     * domain is not a conflict with itself.
     */
    @Transactional
    public Tenant update(String tenantKey, UpdateTenantRequest request, String actorId) {
        Tenant tenant = byKey(tenantKey);

        String subdomain = TenantKey.normalise(request.getSubdomain());
        tenantRepository.findBySubdomain(subdomain).ifPresent(other -> {
            if (!other.getTenantKey().equals(tenantKey)) {
                throw new IllegalArgumentException("Subdomain '" + subdomain + "' is taken");
            }
        });

        String customDomain = normaliseDomain(request.getCustomDomain());
        if (customDomain != null) {
            tenantRepository.findByCustomDomain(customDomain).ifPresent(other -> {
                if (!other.getTenantKey().equals(tenantKey)) {
                    throw new IllegalArgumentException(
                            "Domain '" + customDomain + "' is already in use");
                }
            });
        }

        String previousSubdomain = tenant.getSubdomain();
        String previousDomain = tenant.getCustomDomain();

        tenant.setName(request.getName().trim());
        tenant.setSubdomain(subdomain);
        tenant.setCustomDomain(customDomain);
        tenant.setContactEmail(request.getContactEmail().trim());
        if (request.getPlan() != null && !request.getPlan().isBlank()) {
            tenant.setPlan(request.getPlan().trim());
        }
        if (request.getVertical() != null) {
            tenant.setVertical(request.getVertical());
        }
        tenantRepository.save(tenant);

        publishAfterCommit(tenant);
        // The host the tenant is reached at is the one field other components cache, so a change to
        // it is worth its own line in the log — it is the first thing anyone looks for when a
        // tenant "stopped resolving" a minute after an edit.
        if (!subdomain.equals(previousSubdomain)
                || !java.util.Objects.equals(customDomain, previousDomain)) {
            log.info("Tenant '{}' hosts changed: {} -> {}, domain {} -> {} (by {}). "
                            + "Gateway caches clear within 60s.",
                    tenantKey, previousSubdomain, subdomain, previousDomain, customDomain, actorId);
        }
        log.info("Tenant '{}' identity updated by {}", tenantKey, actorId);
        return tenant;
    }

    @Transactional(readOnly = true)
    public List<Tenant> listAll() {
        return tenantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Tenant byKey(String tenantKey) {
        return tenantRepository.findByTenantKey(tenantKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tenant '" + tenantKey + "'"));
    }

    /**
     * Host-to-tenant lookup for the gateway. Custom domain wins over subdomain so a tenant that
     * has moved to its own domain keeps working while the old subdomain is still pointed at us.
     */
    @Transactional(readOnly = true)
    public Tenant byHost(String host) {
        String hostname = host == null ? "" : host.toLowerCase().split(":")[0];

        return tenantRepository.findByCustomDomain(hostname)
                .or(() -> tenantRepository.findBySubdomain(hostname.split("\\.")[0]))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tenant serves host '" + host + "'"));
    }

    @Transactional
    public Tenant changeStatus(String tenantKey, TenantStatus status, String actorId) {
        Tenant tenant = byKey(tenantKey);

        if (tenant.getTenantKey().equals("platform") && status != TenantStatus.ACTIVE) {
            // Suspending the operator tenant would lock every admin out of the console that is
            // the only way to un-suspend it.
            throw new IllegalArgumentException("The operator tenant cannot be suspended");
        }

        tenant.setStatus(status);
        publishAfterCommit(tenant);
        log.info("Tenant '{}' status -> {} by {}", tenantKey, status, actorId);
        return tenant;
    }

    @Transactional
    public Tenant setModules(String tenantKey, Set<String> moduleKeys, String actorId) {
        Tenant tenant = byKey(tenantKey);
        tenant.setEnabledModules(validated(moduleKeys));
        tenantRepository.save(tenant);

        // Publishing is the whole point of a module change, and this used to be the one write in
        // this class that did not: the row changed, no event went out, and admin-service's
        // ui_tenant_module copy plus the gateway's route gating stayed on the old set until the next
        // restart republished everything. It only ever looked like it worked because the console
        // saved the menu straight afterwards and that event carried the modules along with it.
        publishAfterCommit(tenant);
        log.info("Tenant '{}' modules -> {} by {}", tenantKey, tenant.getEnabledModules(), actorId);
        return tenant;
    }

    private String resolveModules(Vertical vertical, Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return vertical.defaultModules().stream()
                    .map(PlatformModule::key)
                    .collect(Collectors.joining(","));
        }
        return validated(requested);
    }

    private String validated(Set<String> moduleKeys) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : moduleKeys) {
            keys.add(PlatformModule.fromKey(key.trim()).key());
        }
        if (!keys.contains(PlatformModule.AUTH.key())) {
            throw new IllegalArgumentException(
                    "A tenant without the 'auth' module has no way for anyone to sign in");
        }
        return String.join(",", keys);
    }

    /**
     * Replaces this tenant's navigation: which items are hidden, what they are called, what order
     * they sit in, and which one the console opens on.
     *
     * <p>One call rather than a field at a time, and one call for the overrides *and* the landing
     * path, because they are decided together on one screen and a landing path pointing at an item
     * the same save just hid is not a state worth being able to reach.
     *
     * <p>These sit on top of what the module set already decides — a module a tenant does not have
     * takes its menu items with it automatically, and no override here can bring one back. This is
     * for reshaping what remains.
     */
    @Transactional
    public Tenant setNavigation(String tenantKey, List<TenantMenuOverride> requested,
                                String landingPath, String landingItemKey, String actorId) {
        Tenant tenant = byKey(tenantKey);

        List<TenantMenuOverride> overrides = replaceOverrides(tenantKey, requested);

        String landing = blankToNull(landingPath);
        // A landing page the operator has just hidden would open the tenant's console on a screen
        // its own navigation no longer offers — reachable, unnavigable, and indistinguishable from a
        // broken deploy. Refused rather than silently resolved to the dashboard, because the operator
        // picked that screen on purpose and deserves to know the two choices conflict.
        //
        // Checked by item key, which the console sends alongside the path purely for this: the menu
        // catalogue lives in admin-service, inside each tenant's own schema, so nothing here can turn
        // a stored path back into the item it belongs to. Absent, the check is skipped — the console
        // already clears the landing page when the operator hides that item, so this is the second
        // line of defence rather than the only one.
        String landingKey = blankToNull(landingItemKey);
        if (landing != null && landingKey != null) {
            boolean hiddenLanding = overrides.stream()
                    .anyMatch(o -> o.isHidden() && landingKey.equals(o.getItemKey()));
            if (hiddenLanding) {
                throw new IllegalArgumentException(
                        "The landing page '" + landing + "' is one of the items being hidden");
            }
        }
        tenant.setLandingPath(landing);
        tenantRepository.save(tenant);

        publishAfterCommit(tenant, null, overrides);
        log.info("Navigation for tenant '{}' set to {} overrides, landing {} by {}",
                tenantKey, overrides.size(), landing, actorId);
        return tenant;
    }

    /**
     * The overrides for one tenant, in menu order.
     *
     * <p>Sorted by the override's own sort order where it has one, so the console lists them the
     * way the tenant will see them rather than alphabetically by key.
     */
    @Transactional(readOnly = true)
    public List<TenantMenuOverride> menuOverrides(String tenantKey) {
        return menuOverrideRepository.findByIdTenantKeyOrderByIdItemKeyAsc(tenantKey).stream()
                .map(TenantMenuOverrideEntity::toMessage)
                .sorted(java.util.Comparator.comparing(
                        o -> o.getSortOrder() == null ? Integer.MAX_VALUE : o.getSortOrder()))
                .toList();
    }

    /**
     * Writes the tenant's override rows, dropping the ones that say nothing.
     *
     * <p>Replaced wholesale, not merged: the console sends the complete set, and "no longer
     * overridden" is an absence — a merge would leave an item the operator un-hid hidden forever
     * with no row left in the payload to say otherwise.
     *
     * <p>No-op rows are discarded rather than stored. The console builds its editor from every item
     * in the catalogue, so a straight save would write a row for each one, and the table would stop
     * meaning "the operator decided something here" — which is what makes it readable in support.
     */
    private List<TenantMenuOverride> replaceOverrides(String tenantKey,
                                                      List<TenantMenuOverride> requested) {
        menuOverrideRepository.deleteByIdTenantKey(tenantKey);
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }

        java.util.Map<String, TenantMenuOverride> byKey = new java.util.LinkedHashMap<>();
        for (TenantMenuOverride override : requested) {
            if (override == null || override.getItemKey() == null
                    || override.getItemKey().isBlank()) {
                continue;
            }
            if (override.isNoop()) {
                continue;
            }
            String itemKey = override.getItemKey().trim();
            if (itemKey.length() > 64) {
                throw new IllegalArgumentException("Menu item key '" + itemKey + "' is too long");
            }
            String label = blankToNull(override.getLabelOverride());
            if (label != null && label.length() > 120) {
                throw new IllegalArgumentException(
                        "Label for '" + itemKey + "' must be 120 characters or fewer");
            }
            // Last one wins rather than throwing: two rows for the same item is a console bug, not
            // something an operator can act on, and failing the whole save would lose the other
            // forty perfectly good rows with it.
            byKey.put(itemKey, TenantMenuOverride.builder()
                    .itemKey(itemKey)
                    .visible(!override.isHidden())
                    .labelOverride(label)
                    .sortOrder(override.getSortOrder())
                    .build());
        }

        List<TenantMenuOverrideEntity> rows = byKey.values().stream()
                .map(override -> {
                    TenantMenuOverrideEntity entity =
                            new TenantMenuOverrideEntity(tenantKey, override.getItemKey());
                    entity.setVisible(!override.isHidden());
                    entity.setLabelOverride(override.getLabelOverride());
                    entity.setSortOrder(override.getSortOrder());
                    return entity;
                })
                .toList();
        menuOverrideRepository.saveAll(rows);
        return List.copyOf(byKey.values());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Lowercased and stripped; blank becomes null so "cleared" and "never set" are one state. */
    private static String normaliseDomain(String domain) {
        String trimmed = blankToNull(domain);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    /**
     * Republishes every tenant's current state.
     *
     * <p>Called at startup because the module set and menu overrides are things other services
     * keep a synced copy of, and a copy is only as good as the last event that service was up to
     * receive. Publishing the truth on every boot makes the system self-correcting instead of
     * permanently wrong after one missed message. Every consumer is idempotent, so the cost is a
     * few no-op writes.
     */
    @Transactional(readOnly = true)
    public int republishAll() {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            publishAfterCommit(tenant, tenant.branding(), menuOverrides(tenant.getTenantKey()));
        }
        log.info("Republished state for {} tenants", tenants.size());
        return tenants.size();
    }

    /**
     * Replaces a tenant's branding and re-pushes it to their console.
     *
     * <p>Unlike the creation seed, this overwrites whatever theme the tenant currently has — the
     * operator is shown that before confirming. Branding is stored here as well as pushed, so the
     * operator console can show what was last set without a cross-schema read.
     */
    @Transactional
    public Tenant updateBranding(String tenantKey, TenantBranding branding, String actorId) {
        if (branding == null) {
            throw new IllegalArgumentException("Branding is required");
        }
        branding.validate();

        Tenant tenant = byKey(tenantKey);
        tenant.applyBranding(branding);
        tenantRepository.save(tenant);

        publishBrandingUpdate(tenant, branding);
        log.info("Branding for tenant '{}' updated by {}", tenant.getTenantKey(), actorId);
        return tenant;
    }

    /**
     * Carries {@code brandingUpdate}, which is what tells admin-service this may replace a theme
     * the tenant has customised. A creation event never sets it.
     */
    private void publishBrandingUpdate(Tenant tenant, TenantBranding branding) {
        TenantEventMessage event = TenantEventMessage.builder()
                .tenantKey(tenant.getTenantKey())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .status(tenant.getStatus().name())
                .branding(branding)
                .brandingUpdate(true)
                .modules(tenant.moduleKeys())
                .menuOverrides(menuOverrides(tenant.getTenantKey()))
                .landingPath(tenant.getLandingPath())
                .occurredAt(Instant.now())
                .build();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaTemplate.send(TenantTopics.TENANT_EVENTS, event.getTenantKey(), event);
                    }
                });
    }

    /**
     * Published only once the row is committed. Services react by running Flyway against a new
     * schema, and an event for a tenant that then failed to commit would leave orphan schemas
     * across eleven databases.
     */
    private void publishAfterCommit(Tenant tenant) {
        publishAfterCommit(tenant, null, menuOverrides(tenant.getTenantKey()));
    }

    /**
     * @param branding non-null only on creation — it seeds the new tenant's theme, and re-sending
     *                 it on a status change would overwrite whatever the tenant has since chosen
     *                 for itself every time an operator suspended and reactivated them.
     */
    private void publishAfterCommit(Tenant tenant, TenantBranding branding,
                                    List<TenantMenuOverride> overrides) {
        TenantEventMessage event = TenantEventMessage.builder()
                .tenantKey(tenant.getTenantKey())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .status(tenant.getStatus().name())
                .branding(branding == null || branding.isEmpty() ? null : branding)
                .modules(tenant.moduleKeys())
                .menuOverrides(overrides == null ? List.of() : overrides)
                .landingPath(tenant.getLandingPath())
                .occurredAt(Instant.now())
                .build();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaTemplate.send(TenantTopics.TENANT_EVENTS, event.getTenantKey(), event);
                    }
                });
    }
}
