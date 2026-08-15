-- Platform-wide settings, and the three menu items V3 removed.
--
-- V3 dropped admin-reports, admin-invoices and admin-settings because they pointed at screens that
-- had not been built — the entries were dead links. All three now have real pages and real
-- endpoints behind them (AdminReportController, AdminInvoiceController, PlatformSettingsController),
-- so the catalogue rows come back. Their sort orders are the ones V2 gave them, which is what puts
-- them back in the positions the console's layout already expects.

CREATE TABLE IF NOT EXISTS admin_platform_settings (
    setting_key   VARCHAR(64)  NOT NULL,
    -- Always text; the setting's catalogue entry in PlatformSettings decides how it is parsed.
    setting_value VARCHAR(500) NOT NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Deliberately unseeded: a missing row means "the shipped default", so changing a default in a
-- later release reaches every platform that never overrode it.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, sort_order, exact_match, default_roles)
VALUES
('admin-reports',  'Reports',  '/admin/reports',  'Assessment', 'Platform', 160, FALSE, 'SUPER_ADMIN,ADMIN'),
('admin-invoices', 'Invoices', '/admin/invoices', 'Receipt',    'Platform', 170, FALSE, 'SUPER_ADMIN,ADMIN'),
-- Platform settings are Super Admin's alone, matching the endpoint that serves them.
('admin-settings', 'Settings', '/admin/settings', 'Settings',   'Platform', 200, FALSE, 'SUPER_ADMIN');
