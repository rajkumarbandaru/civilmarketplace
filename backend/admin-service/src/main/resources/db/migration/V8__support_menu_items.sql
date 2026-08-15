-- Navigation for the support ticketing screens.
--
-- Two rows, because they are two different surfaces onto the same tickets: members see their own
-- (`/support`, backed by SupportController's reporter-scoped endpoints), staff see the whole
-- queue (`/admin/support`, backed by AdminSupportController). One shared row would have to be
-- visible to everyone and then point somewhere half of them cannot use.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
-- '*' like the other member rows: anyone with an account can need support, and enumerating the
-- role roster here would be a second list to keep in step with the first.
('support',       'Support',       '/support',       'SupportAgent',        'Account',  NULL,
 50,  FALSE, '*'),
-- Grouped with Operations rather than System: working the queue is day-to-day operational work,
-- next to Bookings, not platform configuration.
('admin-support', 'Support queue', '/admin/support', 'ConfirmationNumber',  'Platform', 'Operations',
 145, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN');
