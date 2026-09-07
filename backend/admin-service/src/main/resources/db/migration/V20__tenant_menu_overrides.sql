-- Replaces the tenant's hidden-items table with full menu overrides, and gives it a landing page.
--
-- V19 gave a tenant somewhere to hide menu items. Hiding turned out to be the least useful of the
-- three things an operator wants to say about a tenant's navigation: they also want to reorder the
-- sidebar, and rename a tab to the customer's own vocabulary — "Bookings" is "Site Visits" to one
-- tenant and "Inspections" to another. A table of keys could carry none of that.
--
-- Both tables here are synced copies of operator state, not local truth. tenant-service owns them;
-- these rows exist because the menu is resolved inside each tenant's own schema, where the
-- authoritative answer is a service call away and the resolver has no request to make it on.

CREATE TABLE IF NOT EXISTS ui_tenant_menu_override (
    item_key       VARCHAR(64)  NOT NULL,
    -- FALSE hides the item. NULL is not allowed: an override row that says nothing is dropped by
    -- tenant-service before it is ever published, so every row that arrives here means something.
    visible        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- NULL means "keep the catalogue's own value" for both of these. Deliberately not backfilled
    -- from the catalogue: a row holding a copy of the shipped label would freeze this tenant's
    -- wording against every later release that improves it.
    label_override VARCHAR(120) NULL,
    sort_order     INT          NULL,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Carry across whatever V19's table was holding, as hides.
INSERT IGNORE INTO ui_tenant_menu_override (item_key, visible)
SELECT item_key, FALSE FROM ui_tenant_menu_hidden;

DROP TABLE IF EXISTS ui_tenant_menu_hidden;

-- Where this tenant's console opens. One row, keyed on a constant, because "at most one landing
-- page per tenant" is then something the database guarantees rather than something the service
-- remembers to check.
CREATE TABLE IF NOT EXISTS ui_tenant_navigation (
    scope_key    VARCHAR(20)  NOT NULL,
    landing_path VARCHAR(200) NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Unseeded on purpose: no row means "the shipped dashboard", which is what every tenant that has
-- never been given a landing page should get, including ones created after this migration.
