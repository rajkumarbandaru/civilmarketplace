package com.civileng.marketplace.admin.uiconfig.dto;

import java.util.List;

/**
 * The UI-config wire contract, grouped the way this codebase groups DTOs (one holder per
 * feature, nested records inside). Reads are what clients call on every sign-in; the commands
 * are Super Admin console actions and one member-facing appearance save.
 */
public final class UiConfigDTO {

    private UiConfigDTO() {
    }

    /**
     * Everything one signed-in client needs to paint its shell: which menu entries to show, in
     * what order, and how the workspace should look. Fetched at sign-in and re-fetched when
     * {@code theme().version()} changes.
     */
    public record Snapshot(
            Long userId,
            String role,
            List<ResolvedMenuItem> menu,
            ResolvedTheme theme,
            /**
             * The member's regional preferences, carried on the shell snapshot rather than left to
             * the appearance screen: every page renders dates, so the whole app needs these on
             * first paint, and a second request would mean timestamps visibly re-rendering after
             * the page had settled.
             *
             * <p>Null means "use the browser's own zone / locale default".
             */
            String timezone,
            String dateFormat) {
    }

    /** One side-menu entry as the client should render it, after every overlay is applied. */
    public record ResolvedMenuItem(
            String key,
            String label,
            String path,
            String icon,
            String section,
            /** Sub-heading within the section; null renders the item ungrouped. */
            String menuGroup,
            int sortOrder,
            boolean exactMatch) {
    }

    /**
     * Theme + UI style for one scope, after the workspace row is merged over the platform row.
     * Null fields mean "use the client's built-in default" — deliberately not filled in with
     * server-side guesses, so the shipped MUI theme stays the single source of the base palette.
     */
    public record ResolvedTheme(
            String scopeKey,
            String mode,
            String primaryColor,
            String accentColor,
            String surfaceColor,
            String sidebarColor,
            Integer borderRadius,
            String fontFamily,
            String brandName,
            String logoUrl,
            String uiStyle,
            String buttonStyle,
            String layoutStyle,
            String density,
            int version) {
    }

    /**
     * A named starting point for a theme — the whole palette in one click, so a Super Admin who
     * wants "a dark blue console" does not have to invent six hex codes to get one.
     *
     * <p>The values are a {@link ThemeUpdateCommand}, not a stored row: applying a preset in the
     * console fills the form and still has to be saved, so a preset is never a second source of
     * truth for what a scope currently looks like.
     *
     * @param builtIn true for the presets shipped with the service, false for one a Super Admin
     *                saved. Only the latter can be deleted, and the console reads this rather
     *                than keeping its own list of which keys are ours.
     */
    public record ThemePreset(
            String key,
            String label,
            String description,
            ThemeUpdateCommand values,
            boolean builtIn) {
    }

    /**
     * Saving the theme form as a reusable preset.
     *
     * <p>Only the label is required — a preset with no description is still perfectly usable, and
     * demanding one would just get it filled with the label again. The values carry brand name and
     * logo like any other theme command, and the service drops them: see
     * {@link com.civileng.marketplace.admin.uiconfig.model.CustomThemePreset}.
     */
    public record CustomPresetCommand(
            String label,
            String description,
            ThemeUpdateCommand values) {
    }

    /** One row of the Super Admin's "all workspaces" list. */
    public record WorkspaceSummary(
            String role,
            String label,
            long userCount,
            int visibleMenuCount,
            boolean menuCustomised,
            boolean themeCustomised) {
    }

    /**
     * A new workspace. Only a name and a description: a workspace is a role, and its menu and
     * theme start as the platform defaults, which is what "not customised yet" already means for
     * every existing workspace.
     */
    public record WorkspaceCreateCommand(String name, String description) {
    }

    /**
     * A menu entry as the Super Admin console edits it — the effective value alongside the
     * catalogue default it came from, so the console can show what "reset" would restore.
     */
    public record WorkspaceMenuRow(
            String itemKey,
            String label,
            String defaultLabel,
            String path,
            String icon,
            String section,
            int sortOrder,
            int defaultSortOrder,
            boolean visible,
            boolean defaultVisible,
            boolean customised) {
    }

    /**
     * A menu entry for one specific user. {@code workspaceVisible} is what their role's workspace
     * would show; {@code visible} is what they actually get after their personal override.
     */
    public record UserMenuRow(
            String itemKey,
            String label,
            String path,
            String icon,
            String section,
            int sortOrder,
            boolean visible,
            boolean workspaceVisible,
            boolean overridden) {
    }

    /**
     * What one member's "how my workspace looks" screen needs, in a single read: the appearance
     * being painted, which parts of it the member chose, and — the point of this record — which
     * parts they are not allowed to choose.
     *
     * <p>{@code adminControlled} exists so the client never has to hardcode that list. A screen
     * built from this record renders controls for {@code memberEditable} and plain values for
     * {@code adminControlled}, and stays correct if that split is ever changed here.
     *
     * @param myColorMode the member's own choice, or null when they are following the workspace —
     *                    which is different from the workspace's value happening to match
     */
    public record AppearanceSettings(
            String scopeKey,
            String workspaceLabel,
            ResolvedTheme effective,
            String myColorMode,
            String myDensity,
            String myTimezone,
            String myDateFormat,
            List<String> colorModeOptions,
            List<String> densityOptions,
            List<String> dateFormatOptions,
            List<String> memberEditable,
            List<String> adminControlled) {

        public static final List<String> COLOR_MODES = List.of("light", "dark", "system");
        public static final List<String> DENSITIES = List.of("compact", "comfortable", "spacious");

        /**
         * The date layouts a member may pick, as keys the browser turns into real formats.
         *
         * <p>Deliberately a closed list rather than a free-text pattern: a pattern typed by hand is
         * a pattern that can be invalid, and every date on the site would render as the error.
         */
        public static final List<String> DATE_FORMATS =
                List.of("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD", "D MMM YYYY", "MMM D, YYYY");

        /** The settings a member may change. Everything else in a theme is not. */
        public static final List<String> MEMBER_EDITABLE =
                List.of("colorMode", "density", "timezone", "dateFormat");

        /**
         * Super Admin's fields, in the order a settings screen should list them: the navigation's
         * position first, because that is the change a member is most likely to go looking for.
         */
        public static final List<String> ADMIN_CONTROLLED = List.of(
                "layoutStyle", "uiStyle", "buttonStyle", "primaryColor", "accentColor",
                "surfaceColor", "sidebarColor", "borderRadius", "fontFamily", "brandName",
                "logoUrl");
    }

    // ------------------------------------------------------------------------- write commands

    /**
     * A requested change to one workspace menu entry. Null {@code sortOrder} or
     * {@code labelOverride} means "inherit the catalogue value", which is how a row is partially
     * reset without deleting it.
     */
    public record MenuUpdateCommand(
            String itemKey,
            boolean visible,
            Integer sortOrder,
            String labelOverride) {
    }

    /**
     * A requested change to one user's menu. A null {@code visible} clears the personal override
     * so the user falls back to their workspace's setting.
     */
    public record UserMenuUpdateCommand(String itemKey, Boolean visible) {
    }

    /**
     * A theme save. Every field except {@code mode} is nullable, and null is stored as null —
     * i.e. "inherit", not "black". Clearing a colour in the console is therefore the same
     * operation as never having set it.
     */
    public record ThemeUpdateCommand(
            String mode,
            String primaryColor,
            String accentColor,
            String surfaceColor,
            String sidebarColor,
            Integer borderRadius,
            String fontFamily,
            String brandName,
            String logoUrl,
            String uiStyle,
            String buttonStyle,
            String layoutStyle,
            String density) {
    }

    /**
     * A member changing their own appearance preference. Two fields only — see
     * {@link AppearanceSettings} for why the rest of the theme is not here.
     *
     * <p>A null or blank field means "follow the workspace", so clearing a preference is the same
     * call as setting one. That keeps inheritance intact: a member who clears light/dark picks up
     * whatever Super Admin sets next, instead of being frozen at the value that was current when
     * they cleared it.
     */
    public record AppearanceUpdateCommand(String colorMode, String density,
                                          String timezone, String dateFormat) {
    }
}
