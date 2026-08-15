package com.civileng.marketplace.admin.uiconfig.service;

import com.civileng.marketplace.admin.client.AuthServiceClient;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.*;
import com.civileng.marketplace.admin.uiconfig.model.CustomThemePreset;
import com.civileng.marketplace.admin.uiconfig.model.MenuItemDefinition;
import com.civileng.marketplace.admin.uiconfig.model.ThemeConfig;
import com.civileng.marketplace.admin.uiconfig.model.UserAppearance;
import com.civileng.marketplace.admin.uiconfig.model.UserMenuOverride;
import com.civileng.marketplace.admin.uiconfig.model.WorkspaceMenuEntry;
import com.civileng.marketplace.admin.uiconfig.repository.CustomThemePresetRepository;
import com.civileng.marketplace.admin.uiconfig.repository.MenuItemDefinitionRepository;
import com.civileng.marketplace.admin.uiconfig.repository.ThemeConfigRepository;
import com.civileng.marketplace.admin.uiconfig.repository.UserAppearanceRepository;
import com.civileng.marketplace.admin.uiconfig.repository.UserMenuOverrideRepository;
import com.civileng.marketplace.admin.uiconfig.repository.WorkspaceMenuEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the dynamic UI configuration and applies Super Admin's edits to it.
 *
 * <p>The menu is a three-layer overlay — catalogue defaults, then the workspace (role) overlay,
 * then the member's own visibility overrides — and the theme is a three-layer merge of the
 * platform row, the workspace row, and the two settings a member owns. In both, a null stored
 * value means "inherit", never "empty", which is what lets a later change at a higher layer
 * reach the layers below it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UiConfigService {

    private final MenuItemDefinitionRepository menuItemRepository;
    private final WorkspaceMenuEntryRepository workspaceMenuRepository;
    private final UserMenuOverrideRepository userOverrideRepository;
    private final ThemeConfigRepository themeRepository;
    private final CustomThemePresetRepository customPresetRepository;
    private final UserAppearanceRepository userAppearanceRepository;
    private final RoleDirectory roleDirectory;
    private final AuthServiceClient authServiceClient;

    // ---------------------------------------------------------------- client read

    /** What one signed-in user's shell should look like. The client-facing read. */
    @Transactional(readOnly = true)
    public Snapshot snapshotFor(Long userId, String role) {
        Map<String, UserMenuOverride> overrides = userOverrideRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMenuOverride::getItemKey, Function.identity(), (a, b) -> a));

        // exactMatch is a catalogue-only property (no overlay can change it), so it is looked up
        // from one preloaded map rather than a query per row.
        Map<String, MenuItemDefinition> catalogue = menuItemRepository.findAll().stream()
                .collect(Collectors.toMap(MenuItemDefinition::getItemKey, Function.identity(), (a, b) -> a));

        List<ResolvedMenuItem> menu = effectiveRows(role).stream()
                .filter(row -> {
                    UserMenuOverride override = overrides.get(row.itemKey());
                    return override != null ? override.isVisible() : row.visible();
                })
                .map(row -> new ResolvedMenuItem(
                        row.itemKey(), row.label(), row.path(), row.icon(), row.section(),
                        // Grouping is a catalogue property like exactMatch — no overlay moves an
                        // item between groups, so it is read from the same preloaded map.
                        catalogue.get(row.itemKey()).getMenuGroup(),
                        row.sortOrder(), catalogue.get(row.itemKey()).isExactMatch()))
                .toList();

        return new Snapshot(userId, role, menu, effectiveTheme(userId, role));
    }

    /**
     * The workspace theme with the member's own preference applied last. Only light/dark and
     * density can come from the member; the position of the navigation and the rest of the look
     * stay as Super Admin set them.
     */
    private ResolvedTheme effectiveTheme(Long userId, String role) {
        ResolvedTheme workspace = theme(role);
        UserAppearance mine = userAppearanceRepository.findById(userId).orElse(null);
        if (mine == null || mine.isEmpty()) return workspace;

        return new ResolvedTheme(
                workspace.scopeKey(),
                firstNonNull(mine.getColorMode(), workspace.mode()),
                workspace.primaryColor(), workspace.accentColor(), workspace.surfaceColor(),
                workspace.sidebarColor(), workspace.borderRadius(), workspace.fontFamily(),
                workspace.brandName(), workspace.logoUrl(),
                workspace.uiStyle(), workspace.buttonStyle(), workspace.layoutStyle(),
                firstNonNull(mine.getDensity(), workspace.density()),
                workspace.version());
    }

    // ---------------------------------------------------------------- workspaces

    /** Every role's workspace, including roles that currently have no users. */
    @Transactional(readOnly = true)
    public List<WorkspaceSummary> listWorkspaces() {
        Map<String, Long> counts = roleDirectory.userCounts();
        Set<String> themedScopes = themeRepository.findAll().stream()
                .map(ThemeConfig::getScopeKey)
                .collect(Collectors.toSet());

        // The catalogue is the same for every role, so it is read once here rather than once per
        // workspace — 24 roles would otherwise mean 24 identical queries.
        List<MenuItemDefinition> catalogue = menuItemRepository.findAllByOrderBySortOrderAsc();
        Map<String, List<WorkspaceMenuEntry>> overlaysByRole = workspaceMenuRepository.findAll().stream()
                .collect(Collectors.groupingBy(WorkspaceMenuEntry::getRole));

        return counts.keySet().stream()
                .map(role -> {
                    List<WorkspaceMenuRow> rows =
                            effectiveRows(catalogue, overlaysByRole.getOrDefault(role, List.of()), role);
                    return new WorkspaceSummary(
                            role,
                            humanise(role),
                            counts.getOrDefault(role, 0L),
                            (int) rows.stream().filter(WorkspaceMenuRow::visible).count(),
                            rows.stream().anyMatch(WorkspaceMenuRow::customised),
                            themedScopes.contains(role));
                })
                .toList();
    }

    /**
     * Creates a workspace, which means creating a role — roles live in auth-service, so that is
     * where the write goes. Nothing is stored here: a workspace with no menu overlay and no theme
     * row is exactly a workspace on the platform defaults, and the console already renders that
     * state for every role that has never been customised.
     *
     * @return the new workspace as the list renders it, so the console can show the row without a
     *         second round-trip.
     */
    public WorkspaceSummary createWorkspace(WorkspaceCreateCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("A workspace needs a name");
        }

        Map<String, Object> body;
        try {
            body = authServiceClient.createRole(
                    "SUPER_ADMIN",
                    // Nulls would be rejected by the request body's own validation; the caller's
                    // blank description is simply "no description".
                    Map.of("name", command.name().trim(),
                            "description", blankToNull(command.description()) == null
                                    ? "" : command.description().trim()))
                    .getBody();
        } catch (feign.FeignException e) {
            // Auth-service owns the name rules and uniqueness, so its 4xx is the real answer here
            // — surfacing it as a 400 keeps "that role already exists" readable in the console
            // instead of collapsing into a generic 500.
            if (e.status() >= 400 && e.status() < 500) {
                throw new IllegalArgumentException(feignMessage(e));
            }
            throw e;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> created = body == null ? null : (Map<String, Object>) body.get("data");
        if (created == null || created.get("name") == null) {
            throw new IllegalStateException("auth-service did not return the created role");
        }
        String role = created.get("name").toString();

        // The catalogue is cached for two minutes; without this the workspace it was just asked to
        // create would be absent from the list it reloads next.
        roleDirectory.invalidate();
        log.info("Workspace {} created", role);

        List<WorkspaceMenuRow> rows = effectiveRows(role);
        return new WorkspaceSummary(
                role,
                humanise(role),
                0L,
                (int) rows.stream().filter(WorkspaceMenuRow::visible).count(),
                false,
                false);
    }

    /** The {@code message} auth-service put in its error body, or the raw status line. */
    private static String feignMessage(feign.FeignException e) {
        String content = e.contentUTF8();
        if (content != null && !content.isBlank()) {
            try {
                Object message = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(content, Map.class).get("message");
                if (message != null) return message.toString();
            } catch (Exception ignored) {
                // A non-JSON body is no worse than no body — fall through to the generic message.
            }
        }
        return "The workspace could not be created (auth-service returned " + e.status() + ")";
    }

    // ---------------------------------------------------------------- workspace menu

    /** The editable menu for one workspace: effective values plus their catalogue defaults. */
    @Transactional(readOnly = true)
    public List<WorkspaceMenuRow> workspaceMenu(String role) {
        requireRole(role);
        return effectiveRows(role);
    }

    private List<WorkspaceMenuRow> effectiveRows(String role) {
        return effectiveRows(
                menuItemRepository.findAllByOrderBySortOrderAsc(),
                workspaceMenuRepository.findByRole(role),
                role);
    }

    /**
     * The catalogue with the role's overlay applied — every item, including hidden ones, because
     * the console needs to be able to switch a hidden item back on.
     */
    private List<WorkspaceMenuRow> effectiveRows(List<MenuItemDefinition> catalogue,
                                                 List<WorkspaceMenuEntry> overlayRows,
                                                 String role) {
        Map<String, WorkspaceMenuEntry> overlay = overlayRows.stream()
                .collect(Collectors.toMap(WorkspaceMenuEntry::getItemKey, Function.identity(), (a, b) -> a));

        return catalogue.stream()
                .map(item -> {
                    WorkspaceMenuEntry entry = overlay.get(item.getItemKey());
                    boolean defaultVisible = item.isDefaultVisibleFor(role);
                    boolean visible = entry != null ? entry.isVisible() : defaultVisible;
                    int sortOrder = entry != null && entry.getSortOrder() != null
                            ? entry.getSortOrder() : item.getSortOrder();
                    String label = entry != null && entry.getLabelOverride() != null
                            ? entry.getLabelOverride() : item.getLabel();
                    return new WorkspaceMenuRow(
                            item.getItemKey(), label, item.getLabel(), item.getPath(), item.getIcon(),
                            item.getSection(), sortOrder, item.getSortOrder(), visible, defaultVisible,
                            entry != null);
                })
                .sorted(Comparator.comparingInt(WorkspaceMenuRow::sortOrder))
                .toList();
    }

    @Transactional
    public void updateWorkspaceMenu(String role, List<MenuUpdateCommand> commands) {
        requireRole(role);
        for (MenuUpdateCommand command : commands) {
            MenuItemDefinition item = rawItem(command.itemKey());
            WorkspaceMenuEntry entry = workspaceMenuRepository
                    .findByRoleAndItemKey(role, command.itemKey())
                    .orElseGet(() -> new WorkspaceMenuEntry(role, command.itemKey()));
            entry.setVisible(command.visible());
            entry.setSortOrder(command.sortOrder());
            // Storing an override equal to the catalogue label would make "customised" lie, and
            // would freeze the entry against future copy changes.
            entry.setLabelOverride(
                    command.labelOverride() == null || command.labelOverride().isBlank()
                            || command.labelOverride().equals(item.getLabel())
                            ? null : command.labelOverride());
            workspaceMenuRepository.save(entry);
        }
        log.info("Workspace menu for role {} updated: {} entries", role, commands.size());
    }

    /** Drops every overlay row for the role, returning it to the catalogue defaults. */
    @Transactional
    public void resetWorkspaceMenu(String role) {
        requireRole(role);
        workspaceMenuRepository.deleteByRole(role);
        log.info("Workspace menu for role {} reset to catalogue defaults", role);
    }

    // ---------------------------------------------------------------- per-user menu

    @Transactional(readOnly = true)
    public List<UserMenuRow> userMenu(Long userId, String role) {
        requireRole(role);
        Map<String, UserMenuOverride> overrides = userOverrideRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMenuOverride::getItemKey, Function.identity(), (a, b) -> a));

        return effectiveRows(role).stream()
                .map(row -> {
                    UserMenuOverride override = overrides.get(row.itemKey());
                    return new UserMenuRow(
                            row.itemKey(), row.label(), row.path(), row.icon(), row.section(), row.sortOrder(),
                            override != null ? override.isVisible() : row.visible(),
                            row.visible(),
                            override != null);
                })
                .toList();
    }

    @Transactional
    public void updateUserMenu(Long userId, List<UserMenuUpdateCommand> commands) {
        for (UserMenuUpdateCommand command : commands) {
            rawItem(command.itemKey());
            if (command.visible() == null) {
                userOverrideRepository.findByUserIdAndItemKey(userId, command.itemKey())
                        .ifPresent(userOverrideRepository::delete);
                continue;
            }
            UserMenuOverride override = userOverrideRepository
                    .findByUserIdAndItemKey(userId, command.itemKey())
                    .orElseGet(() -> new UserMenuOverride(userId, command.itemKey(), command.visible()));
            override.setVisible(command.visible());
            userOverrideRepository.save(override);
        }
        log.info("Personal menu overrides for user {} updated: {} entries", userId, commands.size());
    }

    @Transactional
    public void resetUserMenu(Long userId) {
        userOverrideRepository.deleteByUserId(userId);
        log.info("Personal menu overrides for user {} cleared", userId);
    }

    // ---------------------------------------------------------------- theme

    /** The theme a scope actually renders. {@code scopeKey} is {@code PLATFORM} or a role name. */
    @Transactional(readOnly = true)
    public ResolvedTheme theme(String scopeKey) {
        ThemeConfig platform = themeRepository.findById(ThemeConfig.PLATFORM_SCOPE)
                .orElseGet(() -> new ThemeConfig(ThemeConfig.PLATFORM_SCOPE));
        if (ThemeConfig.PLATFORM_SCOPE.equals(scopeKey)) return toResolved(platform, platform.getScopeKey());

        ThemeConfig workspace = themeRepository.findById(scopeKey).orElse(null);
        if (workspace == null) return toResolved(platform, scopeKey);
        return merge(platform, workspace, scopeKey);
    }

    /** The stored row for a scope, unmerged — what the console edits. */
    @Transactional(readOnly = true)
    public ResolvedTheme rawTheme(String scopeKey) {
        return themeRepository.findById(scopeKey)
                .map(config -> toResolved(config, scopeKey))
                // An unset workspace scope has no row at all; report it as all-inherit rather
                // than 404, so the console can render an empty edit form for it.
                .orElseGet(() -> new ResolvedTheme(
                        scopeKey, "system", null, null, null, null, null, null, null, null,
                        null, null, null, null, 0));
    }

    /**
     * The starting points a Super Admin can load into the theme form: the ones shipped with the
     * service first, then the ones this platform saved for itself. Shipped first because that
     * order never changes as presets are added and removed, so the picker does not reshuffle
     * under an admin who has learned where "Midnight" sits.
     */
    @Transactional(readOnly = true)
    public List<ThemePreset> themePresets() {
        List<ThemePreset> presets = new ArrayList<>(ThemePresets.all());
        customPresetRepository.findAllByOrderByLabelAsc().stream()
                .map(UiConfigService::toPreset)
                .forEach(presets::add);
        return presets;
    }

    /**
     * Saves the theme form as a reusable preset.
     *
     * <p>Saving under a label that already exists overwrites that preset rather than failing:
     * "save as My Brand" twice is one admin correcting the first attempt, and a duplicate-name
     * error there would leave them inventing "My Brand 2".
     */
    @Transactional
    public ThemePreset saveCustomPreset(CustomPresetCommand command, Long adminId) {
        String label = blankToNull(command.label());
        if (label == null) {
            throw new IllegalArgumentException("A preset needs a name");
        }
        if (label.length() > 60) {
            throw new IllegalArgumentException("A preset name can be at most 60 characters");
        }

        ThemeUpdateCommand values = command.values();
        if (values == null) {
            throw new IllegalArgumentException("A preset needs theme values");
        }

        CustomThemePreset preset = customPresetRepository.findByLabelIgnoreCase(label)
                .orElseGet(() -> CustomThemePreset.builder().presetKey(newKey(label)).build());

        preset.setLabel(label);
        preset.setDescription(truncate(blankToNull(command.description()), 200));
        preset.setCreatedBy(adminId);
        // Validated exactly like a theme save: a preset that named an unimplemented style would
        // otherwise be storable here and only fail later, when someone tried to apply it.
        preset.setMode(requireOneOf("mode", values.mode(), AppearanceSettings.COLOR_MODES, "system"));
        preset.setPrimaryColor(blankToNull(values.primaryColor()));
        preset.setAccentColor(blankToNull(values.accentColor()));
        preset.setSurfaceColor(blankToNull(values.surfaceColor()));
        preset.setSidebarColor(blankToNull(values.sidebarColor()));
        preset.setBorderRadius(values.borderRadius());
        preset.setFontFamily(blankToNull(values.fontFamily()));
        preset.setUiStyle(requireOneOf("uiStyle", values.uiStyle(), ThemePresets.UI_STYLES, null));
        preset.setButtonStyle(
                requireOneOf("buttonStyle", values.buttonStyle(), ThemePresets.BUTTON_STYLES, null));
        preset.setLayoutStyle(
                requireOneOf("layoutStyle", values.layoutStyle(), ThemePresets.LAYOUT_STYLES, null));
        preset.setDensity(requireOneOf("density", values.density(), AppearanceSettings.DENSITIES, null));

        customPresetRepository.save(preset);
        log.info("Custom theme preset '{}' ({}) saved by admin {}", label, preset.getPresetKey(), adminId);
        return toPreset(preset);
    }

    /** Deletes a saved preset. Nothing that is already painted changes — see the entity. */
    @Transactional
    public void deleteCustomPreset(String key) {
        if (ThemePresets.isBuiltInKey(key)) {
            throw new IllegalArgumentException(
                    "'" + key + "' is a built-in preset and cannot be deleted");
        }
        CustomThemePreset preset = customPresetRepository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("No such preset: " + key));
        customPresetRepository.delete(preset);
        log.info("Custom theme preset '{}' deleted", preset.getLabel());
    }

    /**
     * A slug of the label, suffixed if that slug is taken — a label of nothing but punctuation
     * still has to produce a usable key, hence the fallback.
     */
    private String newKey(String label) {
        String base = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "preset";
        base = truncate(base, 50);

        String key = base;
        for (int suffix = 2; ThemePresets.isBuiltInKey(key) || customPresetRepository.existsById(key); suffix++) {
            key = base + "-" + suffix;
        }
        return key;
    }

    /** Brand name and logo are null by construction: a preset carries a look, not an identity. */
    private static ThemePreset toPreset(CustomThemePreset preset) {
        return new ThemePreset(
                preset.getPresetKey(),
                preset.getLabel(),
                preset.getDescription() == null ? "Saved on this platform." : preset.getDescription(),
                new ThemeUpdateCommand(
                        preset.getMode(), preset.getPrimaryColor(), preset.getAccentColor(),
                        preset.getSurfaceColor(), preset.getSidebarColor(), preset.getBorderRadius(),
                        preset.getFontFamily(), null, null, preset.getUiStyle(),
                        preset.getButtonStyle(), preset.getLayoutStyle(), preset.getDensity()),
                false);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Transactional
    public ResolvedTheme updateTheme(String scopeKey, ThemeUpdateCommand command) {
        if (!ThemeConfig.PLATFORM_SCOPE.equals(scopeKey)) requireRole(scopeKey);

        ThemeConfig config = themeRepository.findById(scopeKey).orElseGet(() -> new ThemeConfig(scopeKey));
        config.setMode(requireOneOf("mode", command.mode(), AppearanceSettings.COLOR_MODES, "system"));
        config.setPrimaryColor(blankToNull(command.primaryColor()));
        config.setAccentColor(blankToNull(command.accentColor()));
        config.setSurfaceColor(blankToNull(command.surfaceColor()));
        config.setSidebarColor(blankToNull(command.sidebarColor()));
        config.setBorderRadius(command.borderRadius());
        config.setFontFamily(blankToNull(command.fontFamily()));
        config.setBrandName(blankToNull(command.brandName()));
        config.setLogoUrl(blankToNull(command.logoUrl()));
        // Style fields are closed sets the client switches on — an unknown value would silently
        // render as the default, so it is refused here instead of being stored and ignored.
        config.setUiStyle(requireOneOf("uiStyle", command.uiStyle(), ThemePresets.UI_STYLES, null));
        config.setButtonStyle(
                requireOneOf("buttonStyle", command.buttonStyle(), ThemePresets.BUTTON_STYLES, null));
        config.setLayoutStyle(
                requireOneOf("layoutStyle", command.layoutStyle(), ThemePresets.LAYOUT_STYLES, null));
        config.setDensity(requireOneOf("density", command.density(), AppearanceSettings.DENSITIES, null));
        config.setVersion(config.getVersion() + 1);
        themeRepository.save(config);
        log.info("Theme for scope {} saved (version {}): mode={} primary={} accent={} layout={} density={}",
                scopeKey, config.getVersion(), config.getMode(), config.getPrimaryColor(),
                config.getAccentColor(), config.getLayoutStyle(), config.getDensity());
        return theme(scopeKey);
    }

    /** Deletes a workspace's theme row so it inherits the platform theme again. */
    @Transactional
    public void resetTheme(String scopeKey) {
        if (ThemeConfig.PLATFORM_SCOPE.equals(scopeKey)) {
            throw new IllegalArgumentException(
                    "The platform theme is the base every workspace inherits and cannot be deleted");
        }
        themeRepository.findById(scopeKey).ifPresent(themeRepository::delete);
        log.info("Theme for scope {} reset to the platform theme", scopeKey);
    }

    // ---------------------------------------------------------------- member's own appearance

    /**
     * One member's appearance screen: what is being painted, what they chose, and what only Super
     * Admin can change. Unlike the methods above, these three are called by the member themselves,
     * not by the console.
     */
    @Transactional(readOnly = true)
    public AppearanceSettings myAppearance(Long userId, String role) {
        UserAppearance mine = userAppearanceRepository.findById(userId).orElse(null);
        return new AppearanceSettings(
                role,
                humanise(role),
                effectiveTheme(userId, role),
                mine == null ? null : mine.getColorMode(),
                mine == null ? null : mine.getDensity(),
                AppearanceSettings.COLOR_MODES,
                AppearanceSettings.DENSITIES,
                AppearanceSettings.MEMBER_EDITABLE,
                AppearanceSettings.ADMIN_CONTROLLED);
    }

    @Transactional
    public AppearanceSettings updateMyAppearance(Long userId, String role, AppearanceUpdateCommand command) {
        String colorMode = requireOneOf("colorMode", command.colorMode(), AppearanceSettings.COLOR_MODES, null);
        String density = requireOneOf("density", command.density(), AppearanceSettings.DENSITIES, null);

        UserAppearance mine = userAppearanceRepository.findById(userId)
                .orElseGet(() -> new UserAppearance(userId));
        mine.setColorMode(colorMode);
        mine.setDensity(density);

        // Both cleared means "follow the workspace on everything", which is the absence of a row
        // rather than a row full of nulls — otherwise every member who ever opened the screen
        // would leave one behind.
        if (mine.isEmpty()) {
            userAppearanceRepository.findById(userId).ifPresent(userAppearanceRepository::delete);
        } else {
            userAppearanceRepository.save(mine);
        }
        return myAppearance(userId, role);
    }

    /** Drops the member's preference row so they follow the workspace on both settings again. */
    @Transactional
    public AppearanceSettings resetMyAppearance(Long userId, String role) {
        userAppearanceRepository.findById(userId).ifPresent(userAppearanceRepository::delete);
        return myAppearance(userId, role);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Blank or null clears the setting (or falls back to {@code fallback} where the column is not
     * nullable); anything else has to be one of the values the client was offered. Rejecting
     * rather than silently ignoring an unknown value matters here because these strings are
     * written straight into the theme the client paints from.
     */
    private static String requireOneOf(String field, String value, List<String> allowed, String fallback) {
        String trimmed = blankToNull(value);
        if (trimmed == null) return fallback;
        if (!allowed.contains(trimmed)) {
            throw new IllegalArgumentException(field + " must be one of " + String.join(", ", allowed));
        }
        return trimmed;
    }

    /** Workspace fields win where set; nulls fall through to the platform row. */
    private static ResolvedTheme merge(ThemeConfig platform, ThemeConfig workspace, String scopeKey) {
        return new ResolvedTheme(
                scopeKey,
                firstNonNull(workspace.getMode(), platform.getMode()),
                firstNonNull(workspace.getPrimaryColor(), platform.getPrimaryColor()),
                firstNonNull(workspace.getAccentColor(), platform.getAccentColor()),
                firstNonNull(workspace.getSurfaceColor(), platform.getSurfaceColor()),
                firstNonNull(workspace.getSidebarColor(), platform.getSidebarColor()),
                firstNonNull(workspace.getBorderRadius(), platform.getBorderRadius()),
                firstNonNull(workspace.getFontFamily(), platform.getFontFamily()),
                firstNonNull(workspace.getBrandName(), platform.getBrandName()),
                firstNonNull(workspace.getLogoUrl(), platform.getLogoUrl()),
                firstNonNull(workspace.getUiStyle(), platform.getUiStyle()),
                firstNonNull(workspace.getButtonStyle(), platform.getButtonStyle()),
                firstNonNull(workspace.getLayoutStyle(), platform.getLayoutStyle()),
                firstNonNull(workspace.getDensity(), platform.getDensity()),
                // Either layer changing should invalidate a client's cached copy.
                platform.getVersion() + workspace.getVersion());
    }

    private static ResolvedTheme toResolved(ThemeConfig config, String scopeKey) {
        return new ResolvedTheme(
                scopeKey, config.getMode(), config.getPrimaryColor(), config.getAccentColor(),
                config.getSurfaceColor(), config.getSidebarColor(), config.getBorderRadius(),
                config.getFontFamily(), config.getBrandName(), config.getLogoUrl(),
                config.getUiStyle(), config.getButtonStyle(), config.getLayoutStyle(),
                config.getDensity(), config.getVersion());
    }

    private MenuItemDefinition rawItem(String itemKey) {
        return menuItemRepository.findByItemKey(itemKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No menu item with key " + itemKey + " — menu entries come from the catalogue, "
                                + "they cannot be invented by an admin"));
    }

    private void requireRole(String role) {
        if (!roleDirectory.exists(role)) {
            throw new IllegalArgumentException("No such workspace: " + role);
        }
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** LABOUR_CONTRACTOR -> "Labour Contractor", matching how the frontend labels roles. */
    static String humanise(String role) {
        List<String> words = new ArrayList<>();
        for (String part : role.toLowerCase().split("_")) {
            words.add(part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", words);
    }
}
