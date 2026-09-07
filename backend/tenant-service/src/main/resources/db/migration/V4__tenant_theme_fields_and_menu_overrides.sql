-- Widens tenant branding to the full theme, and turns the hidden-items CSV into real rows.
--
-- Two things the operator console could not express before.
--
-- 1. Branding was nine fields while admin-service's ui_theme_config (V4 there) has thirteen. An
--    operator onboarding a tenant could pick three colours but not the page background, the corner
--    radius or the font — those stayed null and the tenant landed on the shipped defaults for the
--    most visible parts of its own white-labelling. The columns added here close that gap one for
--    one, so what the operator chooses and what the tenant's theme can hold are the same shape.
--
-- 2. hidden_menu_items was a CSV of keys, which can say "not this tab" and nothing else. Reordering
--    the sidebar and renaming a tab to the customer's own vocabulary are the two things operators
--    actually asked for, and neither fits in a key. The CSV therefore becomes tenant_menu_override,
--    one row per decision, with room for all three.
--
-- preset_key is console-only bookkeeping: which shipped palette this one started from, so the form
-- can say "Ocean, tweaked" rather than presenting six hex codes with no origin.

ALTER TABLE tenants
    ADD COLUMN surface_color VARCHAR(9)   NULL AFTER accent_color,
    ADD COLUMN border_radius INT          NULL AFTER color_mode,
    ADD COLUMN font_family   VARCHAR(200) NULL AFTER border_radius,
    ADD COLUMN brand_name    VARCHAR(60)  NULL AFTER font_family,
    ADD COLUMN preset_key    VARCHAR(60)  NULL AFTER brand_name,
    -- Where the tenant's console opens. NULL means the shipped dashboard.
    ADD COLUMN landing_path  VARCHAR(200) NULL AFTER hidden_menu_items;

-- One operator decision about one menu item for one tenant.
--
-- visible = FALSE hides it; label_override and sort_order reshape it. NULL in either means "keep
-- what the catalogue says", which is why they are nullable rather than backfilled from it — a row
-- that copied the catalogue's own values would silently freeze this tenant's menu against every
-- later change to the shipped catalogue.
CREATE TABLE tenant_menu_override (
    tenant_key     VARCHAR(31)  NOT NULL,
    item_key       VARCHAR(64)  NOT NULL,
    visible        BOOLEAN      NOT NULL DEFAULT TRUE,
    label_override VARCHAR(120) NULL,
    sort_order     INT          NULL,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_key, item_key),
    CONSTRAINT fk_tenant_menu_override_tenant
        FOREIGN KEY (tenant_key) REFERENCES tenants (tenant_key) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Carry the existing CSV across, so a tenant that had tabs hidden still has them hidden. Almost
-- certainly a no-op — the column was documented as empty for nearly every tenant — but "almost"
-- is not a reason to drop an operator's decision on the floor.
INSERT INTO tenant_menu_override (tenant_key, item_key, visible)
WITH RECURSIVE split AS (
    SELECT tenant_key,
           TRIM(SUBSTRING_INDEX(hidden_menu_items, ',', 1)) AS item,
           CASE WHEN LOCATE(',', hidden_menu_items) > 0
                THEN SUBSTRING(hidden_menu_items, LOCATE(',', hidden_menu_items) + 1)
                ELSE NULL END AS rest
      FROM tenants
     WHERE hidden_menu_items IS NOT NULL AND hidden_menu_items <> ''
    UNION ALL
    SELECT tenant_key,
           TRIM(SUBSTRING_INDEX(rest, ',', 1)),
           CASE WHEN LOCATE(',', rest) > 0
                THEN SUBSTRING(rest, LOCATE(',', rest) + 1)
                ELSE NULL END
      FROM split
     WHERE rest IS NOT NULL AND rest <> ''
)
SELECT tenant_key, item, FALSE FROM split WHERE item <> '';

ALTER TABLE tenants DROP COLUMN hidden_menu_items;
