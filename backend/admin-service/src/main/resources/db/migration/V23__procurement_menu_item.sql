-- Navigation for B2B procurement (procurement-service). In Work next to Live Tracking: it is what
-- a contractor or supplier does day to day, not administration. Everyone may see it — what they
-- can do inside is decided by the organizations they belong to — but only tenants running the
-- module get it.
INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles, required_module)
VALUES
('procurement', 'Procurement', '/procurement', 'Inventory', 'Work', NULL, 27, FALSE, '*', 'procurement');
