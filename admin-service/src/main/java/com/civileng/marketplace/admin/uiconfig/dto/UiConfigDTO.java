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
            ResolvedTheme theme) {
    }

    /** One side-menu entry as the client should render it, after every overlay is applied. */
    public record ResolvedMenuItem(
            String key,
            String label,
            String path,
            String icon,
            String section,
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
     */
    public record ThemePreset(
            String key,
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
            List<String> colorModeOptions,
            List<String> densityOptions,
            List<String> memberEditable,
            List<String> adminControlled) {

        public static final List<String> COLOR_MODES = List.of("light", "dark", "system");
        public static final List<String> DENSITIES = List.of("compact", "comfortable", "spacious");

        /** The two settings a member may change. Everything else in a theme is not. */
        public static final List<String> MEMBER_EDITABLE = List.of("colorMode", "density");

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
    public record AppearanceUpdateCommand(String colorMode, String density) {
    }
}
