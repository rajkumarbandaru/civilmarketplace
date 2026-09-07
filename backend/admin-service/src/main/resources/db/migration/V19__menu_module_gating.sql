-- Links each menu item to the module it needs, and gives a tenant somewhere to hide items.
--
-- Until now the menu and the module set were unrelated systems: modules gated the API at the
-- gateway, while Flyway seeded the identical marketplace catalogue into every tenant's schema. A
-- FEE_COLLECTION tenant such as `hostelfee` therefore had Bookings, Services, Categories and Live
-- Tracking in its sidebar — every one of them a route the gateway 404s for that tenant — and no
-- entry at all for the residents/fee-plan/invoice modules it actually has.
--
-- A NULL required_module means horizontal: Dashboard, Profile, Settings and the like, which every
-- tenant has whatever it bought.

ALTER TABLE ui_menu_items
    ADD COLUMN required_module VARCHAR(40) NULL AFTER default_roles;

-- Marketplace vertical.
UPDATE ui_menu_items SET required_module = 'bookings'
 WHERE item_key IN ('services', 'tracking', 'admin-categories', 'admin-services',
                    'admin-bookings', 'admin-tracking');
UPDATE ui_menu_items SET required_module = 'projects'  WHERE item_key IN ('admin-projects');
UPDATE ui_menu_items SET required_module = 'reviews'   WHERE item_key IN ('admin-reviews');
UPDATE ui_menu_items SET required_module = 'search'    WHERE item_key IN ('admin-search');

-- Horizontal modules that are nonetheless switchable per tenant.
UPDATE ui_menu_items SET required_module = 'support'
 WHERE item_key IN ('support', 'admin-support');
UPDATE ui_menu_items SET required_module = 'notifications'
 WHERE item_key IN ('admin-alerts', 'admin-emails', 'admin-email-templates');
UPDATE ui_menu_items SET required_module = 'payments'
 WHERE item_key IN ('admin-revenue', 'admin-invoices');

-- Tenant administration belongs to the operator tenant alone. This is the row that was showing a
-- Tenants tab inside every customer tenant, where it 403s on click.
UPDATE ui_menu_items SET required_module = 'tenantadmin' WHERE item_key = 'admin-tenants';

-- The tenant's own module set, synced from tenant.events. Empty means "not yet told", and the
-- resolver deliberately treats that as no filtering rather than as an empty menu — a tenant whose
-- sync has not arrived must not lose its navigation.
CREATE TABLE IF NOT EXISTS ui_tenant_module (
    module_key VARCHAR(40) NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (module_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Items the operator has switched off for this tenant on top of the module rules.
CREATE TABLE IF NOT EXISTS ui_tenant_menu_hidden (
    item_key   VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
