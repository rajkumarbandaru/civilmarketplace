-- The Emails screen now covers every channel, so its label was wrong.
--
-- It was built for email, but SMS, WhatsApp and in-app messages were leaving no record anywhere
-- the console could see. Now that all four are logged in one place, "Emails" undersells it and an
-- admin looking for "did the OTP text go out?" would not think to open it.
--
-- The path stays /admin/notifications-era naming out of scope: /admin/emails is already bookmarked
-- and the route works, so only the label and icon change. Renaming a working URL to match a label
-- is churn an admin pays for in broken bookmarks.

UPDATE ui_menu_items
SET label = 'Notifications',
    icon  = 'NotificationsActive'
WHERE item_key = 'admin-emails';
