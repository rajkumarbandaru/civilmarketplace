-- SUPER_ADMIN is split in two.
--
-- It meant both "owner of this business's workspace" and "RK Technologies staff who run the
-- platform", told apart only by which tenant the account was in. Now:
--
--   TENANT_OWNER      the top role inside a customer tenant (CivEngMarket, a B2B tenant, ...)
--   PLATFORM_OWNER    RK Technologies: full control of the platform and its staff
--   PLATFORM_ADMIN    RK Technologies: runs tenants day to day
--   PLATFORM_SUPPORT  RK Technologies: read-only across the console
--
-- Every tenant's schema runs this migration and gets all four rows; auth-service only lets the
-- platform roles be assigned in the operator tenant (RoleAssignmentPolicy), and every gate checks
-- the tenant as well as the role (web-common PlatformRoles).

UPDATE roles
   SET name = 'TENANT_OWNER', description = 'Owner of this workspace: its staff, settings, theme and content'
 WHERE name = 'SUPER_ADMIN';

INSERT INTO roles (name, description, is_system_role)
SELECT 'TENANT_OWNER', 'Owner of this workspace: its staff, settings, theme and content', TRUE FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TENANT_OWNER');

INSERT INTO roles (name, description, is_system_role)
SELECT 'PLATFORM_OWNER', 'RK Technologies platform owner: every tenant and the platform staff', TRUE FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PLATFORM_OWNER');

INSERT INTO roles (name, description, is_system_role)
SELECT 'PLATFORM_ADMIN', 'RK Technologies platform admin: onboards and runs tenants', TRUE FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PLATFORM_ADMIN');

INSERT INTO roles (name, description, is_system_role)
SELECT 'PLATFORM_SUPPORT', 'RK Technologies platform support: read-only across the console', TRUE FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PLATFORM_SUPPORT');

-- In the operator tenant's schema (auth_db_platform), the staff who were running the platform
-- move onto the platform roles: the old super admin becomes the platform owner, admins become
-- platform admins, and the narrower admin roles become read-only support. Flyway runs with this
-- schema as the connection's default, so DATABASE() names it.
UPDATE users u
  JOIN roles old_role ON old_role.id = u.role_id AND old_role.name = 'TENANT_OWNER'
  JOIN roles new_role ON new_role.name = 'PLATFORM_OWNER'
   SET u.role_id = new_role.id
 WHERE DATABASE() LIKE '%\_platform';

UPDATE users u
  JOIN roles old_role ON old_role.id = u.role_id AND old_role.name = 'ADMIN'
  JOIN roles new_role ON new_role.name = 'PLATFORM_ADMIN'
   SET u.role_id = new_role.id
 WHERE DATABASE() LIKE '%\_platform';

UPDATE users u
  JOIN roles old_role ON old_role.id = u.role_id AND old_role.name IN ('SUB_ADMIN', 'REGIONAL_ADMIN')
  JOIN roles new_role ON new_role.name = 'PLATFORM_SUPPORT'
   SET u.role_id = new_role.id
 WHERE DATABASE() LIKE '%\_platform';
