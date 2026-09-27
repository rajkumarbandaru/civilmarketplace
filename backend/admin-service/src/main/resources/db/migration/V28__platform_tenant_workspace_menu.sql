-- The platform console can work inside a customer tenant.
--
-- Platform staff pick a tenant on the console and then run that tenant's own screens against its
-- data: the gateway switches the request over to the tenant (X-Acting-Tenant, checked there and in
-- every service's AdminRoleInterceptor). This adds those screens to the operator tenant's sidebar,
-- under their own group, at /admin/tenant/... so they never collide with the console's own
-- /admin/users (platform staff) or /admin/theme (the platform's branding).
--
-- Only the operator tenant's copy of the catalogue changes (admin_db_platform), and the rows are
-- gated on the tenantadmin module besides, so no customer tenant could ever show them.
--
-- Roles follow what the gateway allows while acting: owners and admins change things, support
-- reads. The owner-only screens (look, content, templates, role menus, settings) are for the
-- platform owner and admin alone, as a tenant's are for its owner.

SET @rk = (DATABASE() LIKE '%\_platform');

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles, required_module)
SELECT item_key, label, path, icon, 'Platform', 'Tenant workspace', sort_order, FALSE, roles, 'tenantadmin'
  FROM (SELECT 'tenant-overview' AS item_key, 'Tenant dashboard' AS label, '/admin/tenant' AS path,
               'SpaceDashboard' AS icon, 300 AS sort_order, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT' AS roles
        UNION ALL SELECT 'tenant-users', 'Users', '/admin/tenant/users', 'People', 301, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-kyc', 'KYC review', '/admin/tenant/kyc', 'VerifiedUser', 302, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-activity', 'User Activity', '/admin/tenant/activity', 'History', 303, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-categories', 'Categories', '/admin/tenant/categories', 'Category', 304, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-services', 'Services', '/admin/tenant/services', 'Handyman', 305, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-bookings', 'Bookings', '/admin/tenant/bookings', 'BookOnline', 306, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-tracking', 'Live Tracking', '/admin/tenant/tracking', 'NearMe', 307, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-analytics', 'Analytics', '/admin/tenant/analytics', 'Analytics', 308, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-support', 'Support queue', '/admin/tenant/support', 'ConfirmationNumber', 309, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-revenue', 'Revenue', '/admin/tenant/revenue', 'AccountBalanceWallet', 310, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-reports', 'Reports', '/admin/tenant/reports', 'Assessment', 311, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-invoices', 'Invoices', '/admin/tenant/invoices', 'Receipt', 312, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-emails', 'Notifications', '/admin/tenant/emails', 'NotificationsActive', 313, 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
        UNION ALL SELECT 'tenant-alerts', 'Alerts', '/admin/tenant/alerts', 'Campaign', 314, 'PLATFORM_OWNER,PLATFORM_ADMIN'
        UNION ALL SELECT 'tenant-workspace-settings', 'Workspace settings', '/admin/tenant/workspace-settings', 'Tune', 315, 'PLATFORM_OWNER,PLATFORM_ADMIN'
        UNION ALL SELECT 'tenant-workspaces', 'Role menus & workspaces', '/admin/tenant/workspaces', 'ViewQuilt', 316, 'PLATFORM_OWNER,PLATFORM_ADMIN'
        UNION ALL SELECT 'tenant-content', 'Site Content', '/admin/tenant/content', 'Article', 317, 'PLATFORM_OWNER,PLATFORM_ADMIN'
        UNION ALL SELECT 'tenant-email-templates', 'Email Templates', '/admin/tenant/email-templates', 'MarkEmailRead', 318, 'PLATFORM_OWNER,PLATFORM_ADMIN'
        UNION ALL SELECT 'tenant-theme', 'Theme & UI style', '/admin/tenant/theme', 'Palette', 319, 'PLATFORM_OWNER,PLATFORM_ADMIN'
        UNION ALL SELECT 'tenant-settings', 'Settings', '/admin/tenant/settings', 'Settings', 320, 'PLATFORM_OWNER,PLATFORM_ADMIN'
       ) AS items
 WHERE @rk;

-- The dashboard entry must not light up for every /admin/tenant/... page.
UPDATE ui_menu_items SET exact_match = TRUE WHERE @rk AND item_key = 'tenant-overview';
