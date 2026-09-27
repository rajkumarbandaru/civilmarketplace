-- The RK Technologies platform console gets its own sidebar.
--
-- Every tenant's schema holds a copy of the menu catalogue, and until now the operator tenant's
-- copy was the marketplace's: KYC review, revenue, invoices, reports, site content, categories...
-- screens for running a business the RK console does not have. This reshapes only the operator
-- tenant's copy (admin_db_platform); every other tenant's catalogue is untouched, because the
-- whole migration is a no-op outside that schema.
--
--   Overview   Dashboard (the platform dashboard)
--   Tenants    Tenants · Create tenant · Plans & subscriptions
--   Staff      Platform staff · Staff workspaces
--   Insights   Platform analytics
--   Operations Support queue · Notifications · Alerts · Email templates
--   Settings   RK branding & theme · Settings
--
-- MySQL cannot branch in a plain script, so each statement carries the schema test itself.

SET @rk = (DATABASE() LIKE '%\_platform');

-- A business's screens: nothing to run on the console.
DELETE FROM ui_menu_items
 WHERE @rk
   AND item_key IN ('admin-kyc', 'admin-activity', 'admin-categories', 'admin-services', 'admin-bookings',
                    'admin-tracking', 'admin-analytics', 'admin-revenue', 'admin-reports', 'admin-invoices',
                    'admin-workspace-settings', 'admin-content',
                    'services', 'tracking', 'procurement');

-- Platform-only screens. Gated on the tenantadmin module too, so even a copy of these rows
-- could never show up in a customer tenant.
INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles, required_module)
SELECT item_key, label, path, icon, 'Platform', menu_group, sort_order, FALSE,
       'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT', 'tenantadmin'
  FROM (SELECT 'platform-new-tenant' AS item_key, 'Create tenant' AS label, '/admin/tenants/new' AS path,
               'AddBusiness' AS icon, 'Tenants' AS menu_group, 112 AS sort_order
        UNION ALL SELECT 'platform-plans', 'Plans & subscriptions', '/admin/plans', 'WorkspacePremium', 'Tenants', 114
        UNION ALL SELECT 'platform-analytics', 'Platform analytics', '/admin/platform-analytics', 'Insights', 'Insights', 150
       ) AS items
 WHERE @rk;

-- The screens both kinds of console share, regrouped and renamed for the platform.
UPDATE ui_menu_items SET menu_group = 'Overview', sort_order = 100 WHERE @rk AND item_key = 'admin-overview';
UPDATE ui_menu_items SET menu_group = 'Tenants', sort_order = 110, exact_match = TRUE
 WHERE @rk AND item_key = 'admin-tenants';
UPDATE ui_menu_items SET label = 'Platform staff', icon = 'Badge', menu_group = 'Staff', sort_order = 130
 WHERE @rk AND item_key = 'admin-users';
UPDATE ui_menu_items SET label = 'Staff workspaces', menu_group = 'Staff', sort_order = 135
 WHERE @rk AND item_key = 'admin-workspaces';
UPDATE ui_menu_items SET menu_group = 'Operations', sort_order = 160 WHERE @rk AND item_key = 'admin-support';
UPDATE ui_menu_items SET menu_group = 'Operations', sort_order = 165 WHERE @rk AND item_key = 'admin-emails';
UPDATE ui_menu_items SET menu_group = 'Operations', sort_order = 170 WHERE @rk AND item_key = 'admin-alerts';
UPDATE ui_menu_items SET menu_group = 'Operations', sort_order = 175 WHERE @rk AND item_key = 'admin-email-templates';
UPDATE ui_menu_items SET label = 'RK branding & theme', menu_group = 'Settings', sort_order = 190
 WHERE @rk AND item_key = 'admin-theme';
UPDATE ui_menu_items SET menu_group = 'Settings', sort_order = 200 WHERE @rk AND item_key = 'admin-settings';
