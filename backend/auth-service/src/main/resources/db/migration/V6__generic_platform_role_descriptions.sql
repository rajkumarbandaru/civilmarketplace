-- The platform roles' descriptions named the company. The company's name lives in one place — the
-- platform tenant's own branding — so these say what the role does, not whose it is.
UPDATE roles SET description = 'Platform owner: every tenant and the platform staff'
 WHERE name = 'PLATFORM_OWNER';
UPDATE roles SET description = 'Platform admin: onboards and runs tenants'
 WHERE name = 'PLATFORM_ADMIN';
UPDATE roles SET description = 'Platform support: read-only across the platform console'
 WHERE name = 'PLATFORM_SUPPORT';
