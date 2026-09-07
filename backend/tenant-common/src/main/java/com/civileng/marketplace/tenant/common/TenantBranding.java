package com.civileng.marketplace.tenant.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * How a tenant looks: the logo, colours and UI styling chosen when it is onboarded.
 *
 * <p>Lives in tenant-common because two services need the same shape — tenant-service stores it on
 * the tenant record and puts it on the {@code tenant.events} message, and admin-service reads it
 * back to seed the new tenant's theme. Nothing renders from this class directly; it is the
 * hand-off between "who this tenant is" and "what its console looks like".
 *
 * <p>The closed sets below are duplicated from admin-service's {@code ThemePresets} and
 * {@code AppearanceSettings} rather than shared, because tenant-common must not depend on
 * admin-service. They are asserted equal by {@code TenantBrandingOptionsTest} in admin-service, so
 * the copy cannot drift silently.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBranding {

    public static final List<String> COLOR_MODES = List.of("light", "dark", "system");
    public static final List<String> UI_STYLES = List.of("default", "flat", "elevated");
    public static final List<String> BUTTON_STYLES = List.of("gradient", "solid", "outlined");
    public static final List<String> LAYOUT_STYLES =
            List.of("sidebar-left", "sidebar-right", "topbar");
    public static final List<String> DENSITIES = List.of("compact", "comfortable", "spacious");

    /** `#RRGGBB` or `#RRGGBBAA`, matching the width of admin-service's colour columns. */
    private static final java.util.regex.Pattern COLOR =
            java.util.regex.Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /** Absolute or app-relative image URL shown beside the wordmark. */
    private String logoUrl;

    private String primaryColor;
    private String accentColor;

    /** Page/card background. The sidebar is deliberately separate — see {@link #sidebarColor}. */
    private String surfaceColor;

    /**
     * The console's navigation area. Off-palette from {@link #surfaceColor} on purpose: it is the
     * one surface most brands want in their own colour while the content behind it stays neutral.
     */
    private String sidebarColor;

    /** Corner rounding in px. 0 is square; the shipped theme uses 12. */
    private Integer borderRadius;

    /** CSS font stack. Null keeps the shipped face. */
    private String fontFamily;

    /**
     * The wordmark shown in the shell. Null falls back to the tenant's own name, which is what
     * admin-service fills in — see {@code TenantStateSync}. Set it when the legal entity and the
     * brand differ, which for a white-labelled tenant is most of them.
     */
    private String brandName;

    /**
     * Which preset this palette was started from, kept for the operator console alone.
     *
     * <p>Nothing renders from it and nothing re-applies it: once an operator nudges one colour the
     * answer stops being true of the palette. It is here so the console can say "started from
     * Ocean" instead of showing six hex codes with no story, which is the whole reason a preset
     * beat a colour picker in the first place.
     */
    private String presetKey;

    /** light | dark | system */
    private String colorMode;

    /** default | flat | elevated */
    private String uiStyle;

    /** gradient | solid | outlined */
    private String buttonStyle;

    /** sidebar-left | sidebar-right | topbar */
    private String layoutStyle;

    /** compact | comfortable | spacious */
    private String density;

    /** True when nothing was chosen — the tenant then starts on the shipped platform theme. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return logoUrl == null && primaryColor == null && accentColor == null
                && surfaceColor == null && sidebarColor == null && colorMode == null
                && uiStyle == null && buttonStyle == null && layoutStyle == null
                && density == null && borderRadius == null && fontFamily == null
                && brandName == null;
    }

    /**
     * Rejects anything the theme columns could not store or the shell could not render.
     *
     * <p>An unknown style value would be stored and then silently fall back to the default at
     * render time, which is the kind of "saved but did nothing" that takes an afternoon to
     * diagnose — so it fails here, at the boundary, with the field named.
     */
    public void validate() {
        requireColor("primaryColor", primaryColor);
        requireColor("accentColor", accentColor);
        requireColor("surfaceColor", surfaceColor);
        requireColor("sidebarColor", sidebarColor);
        requireOneOf("colorMode", colorMode, COLOR_MODES);
        requireOneOf("uiStyle", uiStyle, UI_STYLES);
        requireOneOf("buttonStyle", buttonStyle, BUTTON_STYLES);
        requireOneOf("layoutStyle", layoutStyle, LAYOUT_STYLES);
        requireOneOf("density", density, DENSITIES);

        if (logoUrl != null && logoUrl.length() > 500) {
            throw new IllegalArgumentException("logoUrl must be 500 characters or fewer");
        }
        // Bounded to what the client can actually paint. A negative radius is not a rounding it
        // renders differently — it is a value the CSS drops, leaving square corners with no hint
        // that the choice was rejected.
        if (borderRadius != null && (borderRadius < 0 || borderRadius > 32)) {
            throw new IllegalArgumentException(
                    "borderRadius must be between 0 and 32, not " + borderRadius);
        }
        requireLength("fontFamily", fontFamily, 200);
        requireLength("brandName", brandName, 60);
        requireLength("presetKey", presetKey, 60);
    }

    private static void requireColor(String field, String value) {
        if (value != null && !COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a hex colour like #1E88E5, not '" + value + "'");
        }
    }

    private static void requireLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        }
    }

    private static void requireOneOf(String field, String value, List<String> allowed) {
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException(
                    field + " must be one of " + allowed + ", not '" + value + "'");
        }
    }
}
