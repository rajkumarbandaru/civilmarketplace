package com.civileng.marketplace.admin.uiconfig.service;

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

    private final ThemeConfigRepository themeRepository;
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

    // Not @Transactional: this is called from onProvisioned on the same object, so a proxy-based
    // annotation would not apply anyway. The two repository calls carry their own transactions,
    // and there is nothing here that needs them to be one.
    private void seed(TenantEventMessage event, TenantBranding branding) {
        ThemeConfig config = themeRepository.findById(ThemeConfig.PLATFORM_SCOPE)
                .orElseGet(() -> new ThemeConfig(ThemeConfig.PLATFORM_SCOPE));

        // An explicit operator edit overwrites regardless: the console shows them that the tenant
        // has customised its theme, and this event only exists because they confirmed anyway.
        if (!event.isBrandingUpdate() && config.getVersion() > 1) {
            log.info("Tenant '{}' has customised its theme (version {}) — leaving it alone",
                    event.getTenantKey(), config.getVersion());
            return;
        }

        config.setLogoUrl(branding.getLogoUrl());
        config.setPrimaryColor(branding.getPrimaryColor());
        config.setAccentColor(branding.getAccentColor());
        config.setSurfaceColor(branding.getSurfaceColor());
        config.setSidebarColor(branding.getSidebarColor());
        config.setBorderRadius(branding.getBorderRadius());
        config.setFontFamily(branding.getFontFamily());
        config.setUiStyle(branding.getUiStyle());
        config.setButtonStyle(branding.getButtonStyle());
        config.setLayoutStyle(branding.getLayoutStyle());
        config.setDensity(branding.getDensity());
        // Never null in the column; an operator who picked no mode gets the shipped default rather
        // than a row that fails to insert.
        config.setMode(branding.getColorMode() == null ? "system" : branding.getColorMode());
        // An explicit wordmark wins; the tenant's own name is the fallback, because it is the one
        // piece of branding an operator has definitely already told us and it beats the shipped
        // product name. The two differ often enough to be worth asking: a white-labelled tenant's
        // legal entity is rarely the brand its own staff see in the sidebar.
        config.setBrandName(branding.getBrandName() != null
                ? truncate(branding.getBrandName(), 60)
                : truncate(event.getName(), 60));
        // Past 1, so this now reads as a deliberate choice to everything downstream — including a
        // redelivered provisioning event, which will leave it alone rather than re-apply. An edit
        // keeps counting up, so the tenant's own screen still shows a customised theme.
        config.setVersion(Math.max(config.getVersion() + 1, 2));

        themeRepository.save(config);
        log.info("{} theme for tenant '{}': primary={} mode={} style={}",
                event.isBrandingUpdate() ? "Updated" : "Seeded",
                event.getTenantKey(), config.getPrimaryColor(), config.getMode(),
                config.getUiStyle());
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
