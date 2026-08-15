-- Groups within a menu section.
--
-- `section` says which navigation surface an item belongs to (Work, Account, Platform); this says
-- where it sits *within* that surface. The console's Platform list had grown to eleven flat rows,
-- which is past the point where a reader scans it rather than reads it — the groups give it the
-- shape it already had implicitly (finance rows together, system rows together).
--
-- Nullable on purpose: an item with no group renders ungrouped, which is what the four
-- member-facing rows want, and what a newly added catalogue row gets until it is placed.

ALTER TABLE ui_menu_items
    ADD COLUMN menu_group VARCHAR(40) NULL AFTER section;

-- The group order is not stored: sort_order already orders the whole section, so a group's
-- position is the position of its first item. That leaves one number to maintain instead of two
-- that can disagree.
UPDATE ui_menu_items SET menu_group = 'Overview'   WHERE item_key = 'admin-overview';
UPDATE ui_menu_items SET menu_group = 'People'     WHERE item_key = 'admin-users';
-- Analytics sits with Operations rather than alone: what it reports on is bookings and categories,
-- and a group of one is a heading with nothing under it.
UPDATE ui_menu_items SET menu_group = 'Operations' WHERE item_key IN ('admin-categories', 'admin-bookings', 'admin-analytics');
UPDATE ui_menu_items SET menu_group = 'Finance'    WHERE item_key IN ('admin-revenue', 'admin-reports', 'admin-invoices');
UPDATE ui_menu_items SET menu_group = 'System'     WHERE item_key IN ('admin-workspaces', 'admin-theme', 'admin-settings');
