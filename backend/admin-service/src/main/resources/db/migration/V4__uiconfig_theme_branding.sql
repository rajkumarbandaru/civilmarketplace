-- Branding and button-style columns for the Super Admin theme.
--
-- Same rule as every other theme column: NULL means "inherit" — the workspace row falls through
-- to the platform row, and the platform row falls through to the client's built-in default.
-- No backfill therefore exists or is wanted; existing rows keep rendering exactly as they did.
ALTER TABLE ui_theme_config
    ADD COLUMN sidebar_color VARCHAR(9)   NULL AFTER surface_color,
    ADD COLUMN button_style  VARCHAR(20)  NULL AFTER ui_style,
    ADD COLUMN brand_name    VARCHAR(60)  NULL AFTER font_family,
    ADD COLUMN logo_url      VARCHAR(500) NULL AFTER brand_name;
