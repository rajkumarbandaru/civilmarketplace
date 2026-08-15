-- Live tracking inside the admin console.
--
-- A second row rather than reusing the member-facing 'tracking' item, because they are different
-- screens answering different questions: the member row points at /track ("where is my plumber"),
-- this one at /admin/tracking ("where is the plumber for booking #482") — the question support is
-- actually asked, by someone who cannot reach the customer's own screen to answer it.
--
-- Grouped with Operations next to Bookings and the Support queue: a staff member fielding "where
-- are they" is already working the queue when the question arrives. sort_order 135 sits it between
-- Bookings (130) and Analytics (140) without renumbering either.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
-- The same four roles that hold Bookings and the Support queue. The tracking endpoint admits any
-- admin role, so nothing here is visible to someone the API would then refuse.
('admin-tracking', 'Live Tracking', '/admin/tracking', 'NearMe', 'Platform', 'Operations',
 135, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN');
