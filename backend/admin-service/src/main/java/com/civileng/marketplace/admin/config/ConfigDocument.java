package com.civileng.marketplace.admin.config;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.AppearanceSettings;
import com.civileng.marketplace.admin.uiconfig.service.ThemePresets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The configuration documents, and the schema of every key in them (architecture 04 §1).
 *
 * <p>The schema is owned by code: which keys exist, their types and bounds, and at which levels
 * they may be set. Separate documents rather than one theme blob, so each is versioned — and can
 * be rolled back — on its own.
 */
public enum ConfigDocument {

    BRANDING("branding", List.of(
            KeySpec.text("brandName", 60),
            KeySpec.url("logoUrl"))),

    THEME("theme", List.of(
            KeySpec.oneOf("mode", AppearanceSettings.COLOR_MODES),
            KeySpec.color("primaryColor"),
            KeySpec.color("accentColor"),
            KeySpec.color("surfaceColor"),
            KeySpec.color("sidebarColor"),
            KeySpec.text("fontFamily", 200))),

    STYLE("style", List.of(
            KeySpec.oneOf("uiStyle", ThemePresets.UI_STYLES),
            KeySpec.oneOf("buttonStyle", ThemePresets.BUTTON_STYLES),
            KeySpec.oneOf("density", AppearanceSettings.DENSITIES),
            KeySpec.integer("borderRadius", 0, 48))),

    LAYOUT("layout", List.of(
            KeySpec.oneOf("layoutStyle", ThemePresets.LAYOUT_STYLES),
            KeySpec.oneOf("siteLayout", ThemePresets.SITE_LAYOUTS)));

    private final String key;
    private final Map<String, KeySpec> keys = new LinkedHashMap<>();

    ConfigDocument(String key, List<KeySpec> specs) {
        this.key = key;
        specs.forEach(spec -> keys.put(spec.name(), spec));
    }

    public String key() {
        return key;
    }

    public Map<String, KeySpec> keys() {
        return keys;
    }

    public static ConfigDocument fromKey(String key) {
        for (ConfigDocument d : values()) {
            if (d.key.equalsIgnoreCase(key)) return d;
        }
        throw new IllegalArgumentException("Unknown configuration document '" + key + "'");
    }

    /** The levels a value may be set at. Secure by default: a key lists every level it allows. */
    public enum Level { TENANT, ROLE }

    public enum Type { TEXT, URL, COLOR, ENUM, INTEGER }

    /**
     * One key's schema and override policy.
     *
     * @param overridableAt where the key may be set; a write at another level is refused
     */
    public record KeySpec(String name, Type type, List<String> allowed, Integer min, Integer max,
                          Set<Level> overridableAt) {

        static KeySpec text(String name, int maxLength) {
            return new KeySpec(name, Type.TEXT, null, null, maxLength, Set.of(Level.TENANT, Level.ROLE));
        }

        static KeySpec url(String name) {
            return new KeySpec(name, Type.URL, null, null, 500, Set.of(Level.TENANT, Level.ROLE));
        }

        static KeySpec color(String name) {
            return new KeySpec(name, Type.COLOR, null, null, null, Set.of(Level.TENANT, Level.ROLE));
        }

        static KeySpec oneOf(String name, List<String> allowed) {
            return new KeySpec(name, Type.ENUM, allowed, null, null, Set.of(Level.TENANT, Level.ROLE));
        }

        static KeySpec integer(String name, int min, int max) {
            return new KeySpec(name, Type.INTEGER, null, min, max, Set.of(Level.TENANT, Level.ROLE));
        }
    }
}
