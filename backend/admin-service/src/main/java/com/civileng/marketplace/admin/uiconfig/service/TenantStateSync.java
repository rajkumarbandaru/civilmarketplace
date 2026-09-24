package com.civileng.marketplace.admin.uiconfig.service;

import com.civileng.marketplace.admin.config.ConfigRelease;
import com.civileng.marketplace.admin.config.ConfigScope;
import com.civileng.marketplace.admin.config.ConfigService;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand;
import com.civileng.marketplace.admin.uiconfig.model.TenantMenuOverrideRow;
import com.civileng.marketplace.admin.uiconfig.model.TenantModule;
import com.civileng.marketplace.admin.uiconfig.model.TenantNavigation;
import com.civileng.marketplace.admin.uiconfig.model.ThemeConfig;
import com.civileng.marketplace.admin.uiconfig.repository.TenantMenuOverrideRowRepository;
import com.civileng.marketplace.admin.uiconfig.repository.TenantModuleRepository;
import com.civileng.marketplace.admin.uiconfig.repository.TenantNavigationRepository;
import com.civileng.marketplace.admin.uiconfig.repository.ThemeConfigRepository;
import com.civileng.marketplace.tenant.common.TenantBranding;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.TenantEventMessage;
import com.civileng.marketplace.tenant.common.TenantProvisionedCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mirrors operator-owned tenant state into the tenant's own schema: its module set, its menu
 * overrides, and — at onboarding — the logo, colours and UI styling that seed its theme.
 *
 * <p>This has to happen here rather than in the create call: the operator is on the {@code
 * platform} tenant when they fill the form, the new tenant's {@code admin_db_<key>} schema does not
 * exist yet at that moment, and the gateway strips any caller-supplied {@code X-Tenant-Id} — so
 * there is no request in which the operator could write another tenant's theme directly. The
 * branding rides the {@code tenant.events} message instead and lands the moment Flyway has built
 * the schema.
 *
 * <p>Two paths land here. A <em>creation</em> event seeds and never overwrites a real choice.
 * An <em>operator edit</em> ({@code brandingUpdate}) replaces the theme outright — the console has
 * already shown the operator that the tenant customised it and they confirmed.
 *
 * <p>For the seeding path: Every tenant starts with a {@code PLATFORM}
 * row — Flyway's own migration inserts one — so "does a row exist?" cannot tell an untouched
 * default from a theme the tenant has since built. {@link ThemeConfig#getVersion()} can:
 * {@code UiConfigService.updateTheme} bumps it on every save, so version 1 means nobody has
 * touched it and anything higher means hands off. Re-provisioning after a missed event is
 * therefore safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.tenant", name = "enabled", havingValue = "true")
public class TenantStateSync implements TenantProvisionedCallback {

    private final ConfigService configService;
    private final TenantModuleRepository moduleRepository;
    private final TenantMenuOverrideRowRepository menuOverrideRepository;
    private final TenantNavigationRepository navigationRepository;

    @Override
    public void onProvisioned(TenantEventMessage event) {
        // The listener thread carries no tenant, and every query below has to land in this
        // tenant's schema rather than wherever the last request left off.
        TenantContext.runAs(event.getTenantKey(), () -> {
            syncMenuInputs(event);

            TenantBranding branding = event.getBranding();
            if (branding != null && !branding.isEmpty()) {
                seed(event, branding);
            }
        });
    }

    /**
     * Mirrors the tenant's module set, menu overrides and landing page into this schema, because
     * the menu is resolved here and the authoritative copy is a service away.
     *
     * <p>Replaced wholesale rather than merged: the event carries the complete set, and a merge
     * would leave a module the operator removed switched on forever — and an item they un-hid still
     * hidden, since "no longer overridden" arrives as an absence and absences do not merge.
     */
    private void syncMenuInputs(TenantEventMessage event) {
        if (event.getModules() != null) {
            moduleRepository.deleteAllInBatch();
            moduleRepository.saveAll(event.getModules().stream().map(TenantModule::new).toList());
        }
        if (event.getMenuOverrides() != null) {
            menuOverrideRepository.deleteAllInBatch();
            menuOverrideRepository.saveAll(event.getMenuOverrides().stream()
                    .filter(override -> override.getItemKey() != null)
                    .map(TenantMenuOverrideRow::from)
                    .toList());
        }

        // Saved even when null, because null is the operator clearing the landing page and the
        // tenant going back to the shipped dashboard — skipping the write on null would make
        // "cleared" the one navigation change that could not be undone.
        navigationRepository.save(new TenantNavigation(event.getLandingPath()));

        log.debug("Synced {} modules, {} menu overrides and landing {} for tenant '{}'",
                event.getModules() == null ? 0 : event.getModules().size(),
                event.getMenuOverrides() == null ? 0 : event.getMenuOverrides().size(),
                event.getLandingPath(), event.getTenantKey());
    }

    /**
     * Publishes the onboarding (or operator-edited) branding as a release of the workspace's theme
     * documents. A creation event never overrides anything but the shipped default — a redelivered
     * event after the tenant has started choosing its own look is ignored — while an operator edit
     * publishes regardless: the console showed them the tenant had customised and they confirmed.
     */
    private void seed(TenantEventMessage event, TenantBranding branding) {
        if (!event.isBrandingUpdate() && configService.hasNonSeedRelease(ConfigScope.TENANT)) {
            log.info("Tenant '{}' already has a published theme beyond the default — leaving it alone",
                    event.getTenantKey());
            return;
        }
        ThemeUpdateCommand command = new ThemeUpdateCommand(
                // An operator who picked no mode gets the shipped default.
                branding.getColorMode() == null ? "system" : branding.getColorMode(),
                branding.getPrimaryColor(), branding.getAccentColor(), branding.getSurfaceColor(),
                branding.getSidebarColor(), branding.getBorderRadius(), branding.getFontFamily(),
                // An explicit wordmark wins; the tenant's own name is the fallback — the one piece
                // of branding an operator has definitely already told us.
                branding.getBrandName() != null ? truncate(branding.getBrandName(), 60) : truncate(event.getName(), 60),
                branding.getLogoUrl(), branding.getUiStyle(), branding.getButtonStyle(),
                branding.getLayoutStyle(), branding.getDensity(), branding.getSiteLayout());
        boolean update = event.isBrandingUpdate();
        configService.publish(ConfigScope.TENANT, UiConfigService.documentsOf(command),
                update ? ConfigRelease.Source.OPERATOR : ConfigRelease.Source.ONBOARDING, null,
                update ? "Branding changed by the platform operator" : "Branding chosen at onboarding", null);
        log.info("{} theme for tenant '{}': primary={} mode={} style={}", update ? "Updated" : "Seeded",
                event.getTenantKey(), branding.getPrimaryColor(), command.mode(), branding.getUiStyle());
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
