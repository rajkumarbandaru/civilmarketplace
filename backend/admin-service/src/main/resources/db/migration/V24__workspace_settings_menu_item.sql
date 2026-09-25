-- Navigation for a workspace's own settings: which modules it runs (within its plan) and which
-- provider accounts it uses — payments, email, SMS, WhatsApp and the AI assistant. For the
-- workspace's own admins; tenant-service scopes every call to the caller's tenant. In System,
-- just after Tenants (175) and before Workspaces (180).
INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
('admin-workspace-settings', 'Workspace settings', '/admin/workspace-settings', 'Tune', 'Platform', 'System',
 177, FALSE, 'SUPER_ADMIN,ADMIN');
