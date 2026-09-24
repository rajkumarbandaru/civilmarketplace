package com.civileng.marketplace.admin.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValidatorTest {

    private static ConfigValidator.Report check(ConfigDocument doc, Map<String, Object> content) {
        return ConfigValidator.validate(doc, ConfigScope.TENANT, content);
    }

    @Test
    void acceptsAWellFormedTheme() {
        assertThat(check(ConfigDocument.THEME, Map.of("mode", "dark", "primaryColor", "#1a73e8")).ok()).isTrue();
        assertThat(check(ConfigDocument.STYLE, Map.of("borderRadius", 12, "uiStyle", "flat")).ok()).isTrue();
        assertThat(check(ConfigDocument.BRANDING, Map.of("logoUrl", "/logo.svg")).ok()).isTrue();
    }

    @Test
    void refusesWhatTheSchemaDoesNot() {
        assertThat(check(ConfigDocument.THEME, Map.of("primaryColor", "blue")).errors())
                .containsExactly("theme.primaryColor must be a hex colour like #1a73e8");
        assertThat(check(ConfigDocument.THEME, Map.of("mode", "neon")).errors()).singleElement().asString().contains("one of");
        assertThat(check(ConfigDocument.STYLE, Map.of("borderRadius", 99)).errors()).singleElement().asString().contains("between 0 and 48");
        assertThat(check(ConfigDocument.STYLE, Map.of("borderRadius", 1.5)).errors()).singleElement().asString().contains("whole number");
        assertThat(check(ConfigDocument.BRANDING, Map.of("logoUrl", "javascript:alert(1)")).ok()).isFalse();
        assertThat(check(ConfigDocument.BRANDING, Map.of("brandName", "x".repeat(61))).ok()).isFalse();
        assertThat(check(ConfigDocument.LAYOUT, Map.of("sidebarPosition", "left")).errors())
                .containsExactly("layout.sidebarPosition is not a known setting");
    }

    @Test
    void warnsButAllowsAPrimaryColourWhiteTextCannotBeReadOn() {
        ConfigValidator.Report r = check(ConfigDocument.THEME, Map.of("primaryColor", "#FFEB3B"));
        assertThat(r.ok()).isTrue();
        assertThat(r.warnings()).singleElement().asString().contains("contrast");
        assertThat(check(ConfigDocument.THEME, Map.of("primaryColor", "#1a237e")).warnings()).isEmpty();
    }

    @Test
    void contrastMatchesWcag() {
        assertThat(ConfigValidator.contrast("#000000", "#FFFFFF")).isEqualTo(21.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(ConfigValidator.contrast("#777777", "#FFFFFF")).isEqualTo(4.48, org.assertj.core.data.Offset.offset(0.01));
    }
}
