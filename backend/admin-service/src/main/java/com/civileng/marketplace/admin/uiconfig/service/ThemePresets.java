package com.civileng.marketplace.admin.uiconfig.service;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemePreset;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand;

import java.util.List;

/**
 * The built-in theme presets and the closed sets of values the style fields accept.
 *
 * <p>Presets are code rather than rows: they are the shipped starting points, versioned with the
 * client that has to be able to render them. A preset that named a layout the shell does not
 * implement would be a broken workspace, so both lists live next to each other here.
 *
 * <p>Applying a preset is a normal theme save — the console fills its form from one and the admin
 * still presses save. Nothing records which preset a scope came from, because after the first
 * edit that answer would be a lie.
 */
public final class ThemePresets {

    private ThemePresets() {
    }

    public static final List<String> UI_STYLES = List.of("default", "flat", "elevated");
    public static final List<String> BUTTON_STYLES = List.of("gradient", "solid", "outlined");
    public static final List<String> LAYOUT_STYLES = List.of("sidebar-left", "sidebar-right", "topbar");

    private static final List<ThemePreset> PRESETS = List.of(
            shipped("civeng", "CivEng Default",
                    "The shipped violet palette, light mode, comfortable spacing.",
                    command("light", "#667eea", "#764ba2", "#ffffff", "#1e293b", 12,
                            "default", "gradient", "comfortable")),
            shipped("ocean", "Ocean",
                    "Cool blues on white, softly rounded — reads calm on long admin sessions.",
                    command("light", "#0284c7", "#06b6d4", "#ffffff", "#0c4a6e", 14,
                            "default", "gradient", "comfortable")),
            shipped("midnight", "Midnight",
                    "Dark surfaces with an indigo accent, for low-light rooms and site offices.",
                    command("dark", "#818cf8", "#c084fc", "#1e293b", "#0f172a", 12,
                            "elevated", "solid", "comfortable")),
            shipped("corporate", "Corporate",
                    "Flat navy, square-ish corners, compact rows — the densest of the presets.",
                    command("light", "#1e3a8a", "#334155", "#ffffff", "#0f172a", 6,
                            "flat", "solid", "compact")),
            shipped("site", "Site Safety",
                    "High-contrast amber on charcoal, sized for gloved hands and bright sun.",
                    command("dark", "#f59e0b", "#ef4444", "#1c1917", "#0c0a09", 8,
                            "elevated", "solid", "spacious")),
            shipped("forest", "Forest",
                    "Deep greens on a warm white — a quieter palette for long survey reviews.",
                    command("light", "#15803d", "#65a30d", "#ffffff", "#14532d", 12,
                            "default", "gradient", "comfortable")),
            shipped("sunset", "Sunset",
                    "Coral into amber, rounded and airy — the warmest of the light presets.",
                    command("light", "#e11d48", "#f97316", "#fffbf7", "#7f1d1d", 16,
                            "elevated", "gradient", "spacious")),
            shipped("graphite", "Graphite",
                    "Neutral greys with a teal accent — brand-free chrome that lets content lead.",
                    command("light", "#334155", "#0d9488", "#f8fafc", "#1f2937", 8,
                            "flat", "outlined", "comfortable")),
            shipped("orchid", "Orchid",
                    "Magenta and violet on near-black, with the navigation across the top.",
                    topbarCommand("dark", "#d946ef", "#8b5cf6", "#18181b", "#09090b", 14,
                            "elevated", "gradient", "comfortable")));

    /** Every preset in this file is shipped with the service, hence {@code builtIn = true}. */
    private static ThemePreset shipped(String key, String label, String description,
                                       ThemeUpdateCommand values) {
        return new ThemePreset(key, label, description, values, true);
    }

    /** The keys reserved by this file — a saved preset may not collide with one. */
    public static boolean isBuiltInKey(String key) {
        return PRESETS.stream().anyMatch(preset -> preset.key().equalsIgnoreCase(key));
    }

    public static List<ThemePreset> all() {
        return PRESETS;
    }

    /**
     * Brand name and logo are deliberately left null by every preset: they are the one part of a
     * theme that is not a style choice, and a preset must never quietly replace a customer's own
     * wordmark with a shipped one.
     */
    private static ThemeUpdateCommand command(String mode, String primary, String accent,
                                              String surface, String sidebar, int radius,
                                              String uiStyle, String buttonStyle, String density) {
        return new ThemeUpdateCommand(mode, primary, accent, surface, sidebar, radius,
                null, null, null, uiStyle, buttonStyle, null, density);
    }

    /**
     * The same as {@link #command} but naming a layout. Most presets leave the layout alone so
     * that trying one out does not move a workspace's navigation under its members; a preset only
     * sets it when the layout is the point of the preset.
     */
    private static ThemeUpdateCommand topbarCommand(String mode, String primary, String accent,
                                                    String surface, String sidebar, int radius,
                                                    String uiStyle, String buttonStyle,
                                                    String density) {
        return new ThemeUpdateCommand(mode, primary, accent, surface, sidebar, radius,
                null, null, null, uiStyle, buttonStyle, "topbar", density);
    }
}
