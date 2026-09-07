-- Navigation for the tenant administration screen.
--
-- tenant-service has exposed /api/v1/tenants since multi-tenancy was built, with nothing in the
-- console calling it — onboarding a tenant meant issuing a curl request by hand, which also meant
-- the operator had to know the `platform`-SUPER_ADMIN rule rather than being shown it.
--
-- Placed in the System group immediately before Workspaces (180), because the two are routinely
-- confused: a workspace is a role within one tenant, a tenant is a whole customer. sort_order 175
-- puts Tenants first, in the order the concepts nest, without renumbering anything.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
-- Super Admin alone, and even then tenant-service refuses anyone whose own tenant is not
-- `platform` — this row only decides who is shown the screen, never who may use it.
('admin-tenants', 'Tenants', '/admin/tenants', 'Domain', 'Platform', 'System',
 175, FALSE, 'SUPER_ADMIN');
