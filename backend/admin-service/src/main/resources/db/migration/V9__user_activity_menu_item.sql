-- Navigation for the platform-wide user activity log (audit-service).
--
-- Placed in the People group directly under Users, because it answers the question the Users
-- screen raises and cannot settle: that screen shows a member's *current* state, with no record of
-- who changed it, when, or from what. Sitting the history next to the roster is what makes it
-- findable — an admin looking at a suspicious account is already on Users when the question occurs
-- to them.
--
-- sort_order 115 puts it between Users (110) and Categories (120) without renumbering either.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
-- Same roles the endpoint itself enforces (AdminAuditController.ADMIN_ROLES). A menu entry
-- visible to someone the API will refuse is worse than no entry: it reads as a broken page
-- rather than as a permission they do not hold.
('admin-activity', 'User Activity', '/admin/activity', 'History', 'Platform', 'People',
 115, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN');
