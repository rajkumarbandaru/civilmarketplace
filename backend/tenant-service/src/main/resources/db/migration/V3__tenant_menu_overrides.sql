-- Per-tenant menu overrides, and the operator tenant's own tenantadmin module.
--
-- The menu a tenant sees is derived from its modules (admin-service V19 links each menu item to
-- the module it needs). This column is the exception list on top of that: items the operator has
-- switched off for one tenant specifically. Empty for almost every tenant, which is why it is a
-- CSV column rather than a table — there is nothing to query it by.

ALTER TABLE tenants
    ADD COLUMN hidden_menu_items TEXT NULL AFTER enabled_modules;

-- `tenantadmin` gates tenant administration itself: the Tenants screen and /api/v1/tenants/**.
-- Only the operator tenant gets it. Without this the screen is seeded into every tenant's menu and
-- a customer's Super Admin sees a Tenants tab that 403s when they click it.
UPDATE tenants
SET enabled_modules = CONCAT(enabled_modules, ',tenantadmin')
WHERE tenant_key = 'platform'
  AND FIND_IN_SET('tenantadmin', enabled_modules) = 0;
