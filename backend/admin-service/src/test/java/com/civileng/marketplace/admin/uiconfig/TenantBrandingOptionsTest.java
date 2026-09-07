package com.civileng.marketplace.admin.uiconfig;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.AppearanceSettings;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemePreset;
import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand;
import com.civileng.marketplace.admin.uiconfig.service.ThemePresets;
import com.civileng.marketplace.tenant.common.TenantBranding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * tenant-common cannot depend on admin-service, so {@link TenantBranding} carries its own copy of
 * the closed sets of style values. This pins the copy to the original.
 *
 * <p>Without it, adding a UI style here and forgetting it there would be silent in both
 * directions: tenant-service would reject a style admin-service accepts, or accept one the shell
 * cannot render and store it on a tenant nobody can explain.
 */
class TenantBrandingOptionsTest {

    @Test
    void colorModesMatchAdminService() {
        assertThat(TenantBranding.COLOR_MODES)
                .containsExactlyElementsOf(AppearanceSettings.COLOR_MODES);
    }

    @Test
    void densitiesMatchAdminService() {
        assertThat(TenantBranding.DENSITIES)
                .containsExactlyElementsOf(AppearanceSettings.DENSITIES);
    }

    @Test
    void uiStylesMatchAdminService() {
        assertThat(TenantBranding.UI_STYLES).containsExactlyElementsOf(ThemePresets.UI_STYLES);
    }

    @Test
    void buttonStylesMatchAdminService() {
        assertThat(TenantBranding.BUTTON_STYLES)
                .containsExactlyElementsOf(ThemePresets.BUTTON_STYLES);
    }

    @Test
    void layoutStylesMatchAdminService() {
        assertThat(TenantBranding.LAYOUT_STYLES)
                .containsExactlyElementsOf(ThemePresets.LAYOUT_STYLES);
    }

    /**
     * Every shipped preset has to survive the trip through {@link TenantBranding}.
     *
     * <p>The operator console offers these presets when a tenant is onboarded, so a preset field
     * that branding cannot carry is a value the operator picks and the tenant never receives —
     * silently, because the rest of the palette arrives and looks plausible. Branding was three
     * colours narrower than a preset until the tenant theme columns were widened, which is exactly
     * how "I chose Corporate but the corners are round" happens.
     */
    @Test
    void everyShippedPresetSurvivesTenantBranding() {
        for (ThemePreset preset : ThemePresets.all()) {
            ThemeUpdateCommand values = preset.values();
            TenantBranding branding = TenantBranding.builder()
                    .colorMode(values.mode())
                    .primaryColor(values.primaryColor())
                    .accentColor(values.accentColor())
                    .surfaceColor(values.surfaceColor())
                    .sidebarColor(values.sidebarColor())
                    .borderRadius(values.borderRadius())
                    .fontFamily(values.fontFamily())
                    .uiStyle(values.uiStyle())
                    .buttonStyle(values.buttonStyle())
                    .layoutStyle(values.layoutStyle())
                    .density(values.density())
                    .presetKey(preset.key())
                    .build();

            // The operator's create call runs exactly this before writing the row, so a preset that
            // fails here is a preset that 400s the tenant it was chosen for.
            branding.validate();

            assertThat(branding.getColorMode()).isEqualTo(values.mode());
            assertThat(branding.getSurfaceColor()).isEqualTo(values.surfaceColor());
            assertThat(branding.getBorderRadius()).isEqualTo(values.borderRadius());
            assertThat(branding.getLayoutStyle()).isEqualTo(values.layoutStyle());
            assertThat(branding.isEmpty())
                    .as("preset '%s' applied nothing at all", preset.key())
                    .isFalse();
        }
    }

    /**
     * The widened fields are the point of the change, so their absence should fail loudly here
     * rather than show up as a tenant whose corners and font quietly stayed default.
     */
    @Test
    void brandingCarriesEveryThemeCommandField() {
        TenantBranding branding = TenantBranding.builder()
                .colorMode("dark")
                .primaryColor("#123456")
                .accentColor("#654321")
                .surfaceColor("#111111")
                .sidebarColor("#000000")
                .borderRadius(8)
                .fontFamily("Inter, sans-serif")
                .brandName("Acme")
                .logoUrl("/logo.png")
                .uiStyle("flat")
                .buttonStyle("solid")
                .layoutStyle("topbar")
                .density("compact")
                .build();

        branding.validate();

        // One field per ThemeUpdateCommand component, minus nothing: if a component is added there
        // and not here, this list stops matching and the mapping in TenantStateSync is incomplete.
        assertThat(ThemeUpdateCommand.class.getRecordComponents()).hasSize(13);
        assertThat(branding.isEmpty()).isFalse();
    }

    @Test
    void borderRadiusOutsideWhatTheClientPaintsIsRejected() {
        TenantBranding branding = TenantBranding.builder().borderRadius(-1).build();

        assertThatThrownBy(branding::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("borderRadius");
    }
}
