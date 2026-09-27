-- The platform console's theme entry named the company; its name is the platform's own branding,
-- edited on that very screen, so the menu says what the screen is instead. Only a label nobody has
-- renamed is changed.
UPDATE ui_menu_items SET label = 'Platform branding & theme'
 WHERE item_key = 'admin-theme' AND label = 'RK branding & theme';
