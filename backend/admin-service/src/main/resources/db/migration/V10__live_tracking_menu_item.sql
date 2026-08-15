-- Navigation for live tracking of the worker or vehicle travelling to a booking.
--
-- Sits in Work, not Account: it is something you do while a job is in progress, next to Dashboard
-- and Services, rather than a setting you configure once. sort_order 25 puts it between Services
-- (20) and Profile (30) without renumbering either.
--
-- '*' like the other member rows. It is worth being explicit about why this is not restricted to
-- customers: the assigned worker needs the same screen to see the destination they are heading to,
-- and support staff handling "where is my plumber" need it to answer the question at all. The
-- endpoint already limits each booking to its own customer, its own worker and admins, so the menu
-- entry does not have to guess at that — and a role list here would only drift from the one the
-- service actually enforces.
--
-- The empty state carries this screen for everyone else: a member with no active booking is told
-- tracking begins once a booking is confirmed and assigned, which is a better answer than a menu
-- entry that is simply absent.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
('tracking', 'Live Tracking', '/track', 'NearMe', 'Work', NULL, 25, FALSE, '*');
