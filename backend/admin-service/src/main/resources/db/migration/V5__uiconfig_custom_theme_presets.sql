-- Super-Admin-authored theme presets, alongside the ones shipped in ThemePresets.
--
-- Same column shape as ui_theme_config minus brand_name/logo_url: those identify a customer
-- rather than styling one, and no preset — shipped or custom — is allowed to carry them, so a
-- saved preset can never quietly replace a workspace's own wordmark.
--
-- NULL keeps its usual meaning: the field is not part of the preset, so applying it leaves
-- whatever the form already had.
CREATE TABLE ui_theme_preset (
    preset_key    VARCHAR(60)  NOT NULL PRIMARY KEY,
    label         VARCHAR(60)  NOT NULL,
    description   VARCHAR(200) NULL,
    mode          VARCHAR(10)  NOT NULL DEFAULT 'system',
    primary_color VARCHAR(9)   NULL,
    accent_color  VARCHAR(9)   NULL,
    surface_color VARCHAR(9)   NULL,
    sidebar_color VARCHAR(9)   NULL,
    border_radius INT          NULL,
    font_family   VARCHAR(200) NULL,
    ui_style      VARCHAR(40)  NULL,
    button_style  VARCHAR(20)  NULL,
    layout_style  VARCHAR(40)  NULL,
    density       VARCHAR(20)  NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NULL,
    updated_at    DATETIME     NULL,
    -- Two presets with the same name in one picker are indistinguishable to the admin choosing
    -- between them, so the label is unique as well as the generated key.
    UNIQUE KEY uk_ui_theme_preset_label (label)
) ENGINE = InnoDB;
