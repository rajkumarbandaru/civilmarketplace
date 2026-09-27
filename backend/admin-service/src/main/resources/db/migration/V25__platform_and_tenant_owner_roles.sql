-- SUPER_ADMIN is split into TENANT_OWNER (the top role inside a tenant) and PLATFORM_OWNER (RK
-- Technologies), with PLATFORM_ADMIN and PLATFORM_SUPPORT below the latter. See auth-service V5.
--
-- The menu catalogue lists roles by name, so every item gets the new names:
--   SUPER_ADMIN            -> TENANT_OWNER and PLATFORM_OWNER (each workspace's owner; the
--                             RK console's workspace is owned by the platform owner)
--   anything ADMIN can see -> PLATFORM_ADMIN and PLATFORM_SUPPORT too (support sees, never writes:
--                             the admin gate refuses its non-GET requests)
-- Items for a module the RK console does not run (bookings, projects, ...) are still filtered out
-- there by module, so this does not put marketplace screens on the console.

UPDATE ui_menu_items
   SET default_roles = CONCAT(default_roles, ',PLATFORM_ADMIN,PLATFORM_SUPPORT')
 WHERE FIND_IN_SET('ADMIN', default_roles) > 0
   AND FIND_IN_SET('PLATFORM_ADMIN', default_roles) = 0;

UPDATE ui_menu_items
   SET default_roles = TRIM(BOTH ',' FROM
       REPLACE(CONCAT(',', default_roles, ','), ',SUPER_ADMIN,', ',TENANT_OWNER,PLATFORM_OWNER,'))
 WHERE FIND_IN_SET('SUPER_ADMIN', default_roles) > 0;

-- Tenant administration is the platform's own job, for all of its staff.
UPDATE ui_menu_items
   SET default_roles = 'PLATFORM_OWNER,PLATFORM_ADMIN,PLATFORM_SUPPORT'
 WHERE item_key = 'admin-tenants';

-- Per-role workspace data follows the role it was saved for. On the operator tenant's schema
-- (admin_db_platform) the old super admin's workspace is the platform owner's; everywhere else it
-- is the tenant owner's. Flyway runs with the target schema as default, so DATABASE() names it.
SET @owner_role = IF(DATABASE() LIKE '%\_platform', 'PLATFORM_OWNER', 'TENANT_OWNER');

UPDATE ui_workspace_menu SET role = @owner_role WHERE role = 'SUPER_ADMIN';
UPDATE ui_theme_config   SET scope_key = @owner_role WHERE scope_key = 'SUPER_ADMIN';
UPDATE config_releases   SET scope = CONCAT('ROLE:', @owner_role) WHERE scope = 'ROLE:SUPER_ADMIN';
UPDATE config_versions   SET scope = CONCAT('ROLE:', @owner_role) WHERE scope = 'ROLE:SUPER_ADMIN';
UPDATE config_pointers   SET scope = CONCAT('ROLE:', @owner_role) WHERE scope = 'ROLE:SUPER_ADMIN';
