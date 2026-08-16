-- Navigation for the platform alerts screen.
--
-- notification-service has had a broadcast endpoint (/api/v1/admin/announcements) since
-- announcements were added, with nothing in the console calling it — sending a platform-wide alert
-- meant issuing a curl request by hand. This row points at the screen that finally does it.
--
-- Placed in the System group beside Notifications (187), because the delivery log answers "did it
-- go out?" for exactly the messages this screen sends. sort_order 188 puts it directly after,
-- without renumbering anything.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
-- Super Admin alone, matching Site Content and the theme: this writes a notification into every
-- targeted user's bell at once, which is a heavier action than anything the operational admin
-- roles do day to day.
('admin-alerts', 'Alerts', '/admin/alerts', 'Campaign', 'Platform', 'System',
 188, FALSE, 'SUPER_ADMIN');
