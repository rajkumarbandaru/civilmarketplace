-- Dynamic UI configuration: the side menu, theme and UI style served to the clients instead of
-- compiled into their bundles, editable by Super Admin. Migrated from the CEP `uiconfig` module.
--
-- Unlike CEP (one database), RAJKUMAR is one database per service — `user_id` here is a plain
-- column with no foreign key, because the users table lives in auth-service's schema. Same rule
-- the booking and review services already follow.

-- The catalogue of every navigable destination the shell knows about. Adding a screen means
-- adding a row here; the Super Admin console then decides who sees it. Admins re-order and hide
-- rows but never invent them — a menu entry pointing at a route the client cannot render is a
-- dead link.
CREATE TABLE IF NOT EXISTS ui_menu_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_key      VARCHAR(64)  NOT NULL,
    label         VARCHAR(120) NOT NULL,
    path          VARCHAR(180) NOT NULL,
    -- A Material-UI icon name (e.g. 'Dashboard'), resolved to a component by the frontend's
    -- icon map. Storing a name rather than a glyph keeps the console's picker honest.
    icon          VARCHAR(60)  NOT NULL,
    section       VARCHAR(40)  NOT NULL,
    sort_order    INT          NOT NULL,
    -- React Router's `end` prop — index routes must not stay highlighted on child pages.
    exact_match   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Comma-separated role names this item ships enabled for, before any admin override.
    -- The single value '*' means every role, so the 24-role platform roster does not have to be
    -- restated (and re-edited) on every member-facing row.
    default_roles TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ui_menu_items_key UNIQUE (item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Per-workspace (= per-role) overlay on the catalogue. A missing row means "use the catalogue
-- default", so an untouched workspace carries no rows at all and picks up later catalogue
-- changes for free.
CREATE TABLE IF NOT EXISTS ui_workspace_menu (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    role           VARCHAR(50)  NOT NULL,
    item_key       VARCHAR(64)  NOT NULL,
    visible        BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INT          NULL,
    label_override VARCHAR(120) NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ui_workspace_menu UNIQUE (role, item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Per-user overlay, applied last: hide or restore a single item for one person without changing
-- what the rest of that workspace sees. Visibility only — ordering and labels stay a
-- workspace-level decision so a role's console stays recognisable from one user to the next.
CREATE TABLE IF NOT EXISTS ui_user_menu_override (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    item_key   VARCHAR(64) NOT NULL,
    visible    BOOLEAN     NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_ui_user_menu_override UNIQUE (user_id, item_key),
    INDEX idx_ui_user_menu_override_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per scope. scope_key is either the literal 'PLATFORM' or a role name — MySQL treats
-- NULLs as distinct in a UNIQUE index, so a nullable `role` column could not enforce
-- "at most one platform row". A NULL field means "inherit", not "no colour".
CREATE TABLE IF NOT EXISTS ui_theme_config (
    scope_key     VARCHAR(50)  NOT NULL PRIMARY KEY,
    mode          VARCHAR(10)  NOT NULL DEFAULT 'system',
    primary_color VARCHAR(9)   NULL,
    accent_color  VARCHAR(9)   NULL,
    surface_color VARCHAR(9)   NULL,
    border_radius INT          NULL,
    font_family   VARCHAR(200) NULL,
    ui_style      VARCHAR(40)  NULL,
    layout_style  VARCHAR(40)  NULL,
    density       VARCHAR(20)  NULL,
    -- Bumped on every save; clients cache by it, and it makes "what changed when" legible.
    version       INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One member's own appearance preference — the last overlay, applied over the workspace theme.
-- Deliberately only two columns: everything about how a workspace looks and is positioned
-- belongs to Super Admin so it stays consistent for everyone in it; a member gets the two
-- settings that are about their own eyes and screen. There is no column for a colour or a layout
-- here, so a member cannot set one even by calling the API directly.
CREATE TABLE IF NOT EXISTS ui_user_appearance (
    user_id    BIGINT      NOT NULL PRIMARY KEY,
    color_mode VARCHAR(10) NULL,
    density    VARCHAR(20) NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Platform-wide defaults. NULL means "the client's own built-in default" rather than a colour
-- chosen here, so an untouched platform theme still renders the shipped MUI design system.
INSERT IGNORE INTO ui_theme_config (scope_key, mode, ui_style, layout_style, density)
VALUES ('PLATFORM', 'light', 'default', 'sidebar-left', 'comfortable');

-- Catalogue seed, mirroring the routes App.tsx actually renders. Icons are Material-UI names.
INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, sort_order, exact_match, default_roles) VALUES
('dashboard',         'Dashboard',          '/dashboard',          'Dashboard',            'Work',     10,  TRUE,  '*'),
('services',          'Services',           '/services',           'Category',             'Work',     20,  FALSE, '*'),
('profile',           'Profile',            '/profile',            'Person',               'Account',  30,  FALSE, '*'),
('appearance',        'Appearance',         '/appearance',         'Palette',              'Account',  40,  FALSE, '*'),
('admin-overview',    'Dashboard',          '/admin',              'Dashboard',            'Platform', 100, TRUE,  'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN'),
('admin-users',       'Users',              '/admin/users',        'People',               'Platform', 110, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN'),
('admin-categories',  'Categories',         '/admin/categories',   'Category',             'Platform', 120, FALSE, 'SUPER_ADMIN,ADMIN'),
('admin-bookings',    'Bookings',           '/admin/bookings',     'BookOnline',           'Platform', 130, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN'),
('admin-analytics',   'Analytics',          '/admin/analytics',    'Analytics',            'Platform', 140, FALSE, 'SUPER_ADMIN,ADMIN'),
('admin-revenue',     'Revenue',            '/admin/revenue',      'AccountBalanceWallet', 'Platform', 150, FALSE, 'SUPER_ADMIN,ADMIN'),
('admin-reports',     'Reports',            '/admin/reports',      'Assessment',           'Platform', 160, FALSE, 'SUPER_ADMIN,ADMIN'),
('admin-invoices',    'Invoices',           '/admin/invoices',     'Receipt',              'Platform', 170, FALSE, 'SUPER_ADMIN,ADMIN'),
('admin-workspaces',  'Workspaces',         '/admin/workspaces',   'ViewQuilt',            'Platform', 180, FALSE, 'SUPER_ADMIN'),
('admin-theme',       'Theme & UI style',   '/admin/theme',        'Palette',              'Platform', 190, FALSE, 'SUPER_ADMIN'),
('admin-settings',    'Settings',           '/admin/settings',     'Settings',             'Platform', 200, FALSE, 'SUPER_ADMIN,ADMIN');
