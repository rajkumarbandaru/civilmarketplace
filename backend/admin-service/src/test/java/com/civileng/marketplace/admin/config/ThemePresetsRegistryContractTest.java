package com.civileng.marketplace.admin.config;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.AppearanceSettings;
import com.civileng.marketplace.admin.uiconfig.service.ThemePresets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keys configuration may choose must be exactly the keys the frontend build can render
 * (architecture 05 §4: styles are code, the choice of style is data). The frontend publishes its
 * registry as registry-manifest.json; drift in either direction fails here.
 */
class ThemePresetsRegistryContractTest {

    private static JsonNode manifest() throws Exception {
        Path p = Path.of("..", "..", "frontend", "src", "experience", "registry-manifest.json");
        if (!Files.exists(p)) p = Path.of("..", "frontend", "src", "experience", "registry-manifest.json");
        return new ObjectMapper().readTree(Files.readString(p));
    }

    private static List<String> list(JsonNode n, String field) {
        List<String> out = new ArrayList<>();
        n.get(field).forEach(v -> out.add(v.asText()));
        return out;
    }

    @Test
    void acceptedValuesMatchWhatTheFrontendCanRender() throws Exception {
        JsonNode m = manifest();
        assertThat(ThemePresets.UI_STYLES).containsExactlyInAnyOrderElementsOf(list(m, "stylePacks"));
        assertThat(ThemePresets.BUTTON_STYLES).containsExactlyInAnyOrderElementsOf(list(m, "buttonStyles"));
        assertThat(ThemePresets.LAYOUT_STYLES).containsExactlyInAnyOrderElementsOf(list(m, "shellLayouts"));
        assertThat(ThemePresets.SITE_LAYOUTS).containsExactlyInAnyOrderElementsOf(list(m, "siteLayouts"));
        assertThat(AppearanceSettings.DENSITIES).containsExactlyInAnyOrderElementsOf(list(m, "densities"));
        assertThat(AppearanceSettings.COLOR_MODES).containsExactlyInAnyOrderElementsOf(list(m, "colorModes"));
    }

    @Test
    void everyShippedPresetIsValidAndTheReferenceExperiencesExist() {
        ThemePresets.all().forEach(p -> com.civileng.marketplace.admin.uiconfig.service.UiConfigService.documentsOf(p.values())
                .forEach((doc, content) -> assertThat(ConfigValidator.validate(doc, ConfigScope.TENANT, content).errors())
                        .as(p.key() + " " + doc.key()).isEmpty()));
        assertThat(ThemePresets.all()).extracting(p -> p.key())
                .contains("aurora-marketplace", "evergreen-corporate", "onyx-boutique");
        var c = ThemePresets.all().stream().filter(p -> p.key().equals("onyx-boutique")).findFirst().orElseThrow().values();
        assertThat(c.uiStyle()).isEqualTo("luxury");
        assertThat(c.siteLayout()).isEqualTo("ecommerce");
    }
}
