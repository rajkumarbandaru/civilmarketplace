-- The role ceiling the platform set for a menu item in this tenant (tenant-service V15), synced
-- from tenant.events. NULL keeps the catalogue's default roles.
ALTER TABLE ui_tenant_menu_override ADD COLUMN roles VARCHAR(1000) NULL;
