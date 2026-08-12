-- admin-reports, admin-invoices and admin-settings were seeded in V2 as catalogue placeholders
-- pointing at RevenuePage/AdminDashboard rather than real pages — a menu entry pointing at a
-- route that isn't really it is a dead link. Dropped until Reports/Invoices/Settings are actually
-- built, per the UI-config remaining-work note in MODULE_STATUS.md.

DELETE FROM ui_user_menu_override WHERE item_key IN ('admin-reports', 'admin-invoices', 'admin-settings');
DELETE FROM ui_workspace_menu WHERE item_key IN ('admin-reports', 'admin-invoices', 'admin-settings');
DELETE FROM ui_menu_items WHERE item_key IN ('admin-reports', 'admin-invoices', 'admin-settings');
