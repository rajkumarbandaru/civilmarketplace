-- Navigation for the KYC review queue (user-service AdminKycController).
--
-- In the People group under Users (110): reviewing someone's documents is a question about a
-- member, asked from the roster. Roles match StaffRoles, which is what the endpoint enforces.
-- No required_module: accounts and verification belong to every tenant.

INSERT IGNORE INTO ui_menu_items
    (item_key, label, path, icon, section, menu_group, sort_order, exact_match, default_roles)
VALUES
('admin-kyc', 'KYC review', '/admin/kyc', 'VerifiedUser', 'Platform', 'People',
 112, FALSE, 'SUPER_ADMIN,ADMIN,SUB_ADMIN,REGIONAL_ADMIN');
