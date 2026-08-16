-- The service catalogue in the admin console.
--
-- The console had Categories but nothing underneath them: the services themselves lived in a
-- hard-coded frontend array, so an admin could rename "Materials" and still not touch the forty
-- items inside it. This row points at the screen that manages those items.
--
-- sort_order 125 sits it directly under Categories (120) and above Bookings (130), which is the
-- order the work happens in — a category exists so services can hang off it.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
-- The same roles that hold Categories: editing what the marketplace sells is the same authority as
-- editing how it is grouped.
('admin-services', 'Services', '/admin/services', 'Handyman', 'Platform', 'Operations',
 125, FALSE, 'SUPER_ADMIN,ADMIN');
