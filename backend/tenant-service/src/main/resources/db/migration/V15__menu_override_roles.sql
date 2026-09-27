-- The platform decides which roles see a menu item in a tenant, not only whether anyone does.
--
-- Comma-separated role names; NULL keeps the catalogue's own default roles. A ceiling: the tenant's
-- own admins can narrow it further in their workspace menus but cannot widen it. Synced to
-- admin-service's ui_tenant_menu_override with the rest of the row on tenant.events.
ALTER TABLE tenant_menu_override ADD COLUMN roles VARCHAR(1000) NULL;
