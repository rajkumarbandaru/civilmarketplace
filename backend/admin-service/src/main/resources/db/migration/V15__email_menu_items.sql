-- Email in the admin console: the templates, and the log of what was actually sent.
--
-- Transactional email was invisible from the console — the templates were Thymeleaf files in the
-- notification-service jar, so rewording a booking confirmation meant a redeploy, and "did the
-- customer get it?" had no answer short of reading service logs. These two rows point at the
-- screens that fix both halves.
--
-- Grouped under System next to Site Content (185): editing the mail the platform sends is the same
-- kind of authority as editing the copy on the public site, not day-to-day Operations work.
--
-- Roles differ between the two on purpose. Templates are Super Admin's alone, matching Site
-- Content — an edit there changes what every future customer receives. The delivery log is open to
-- the operational admin roles, because "was the confirmation delivered?" is a support question and
-- the screen is read-only.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
('admin-email-templates', 'Email Templates', '/admin/email-templates', 'MarkEmailRead',
 'Platform', 'System', 186, FALSE, 'SUPER_ADMIN'),
('admin-emails', 'Emails', '/admin/emails', 'Email',
 'Platform', 'System', 187, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN');
