/**
 * The two staff tiers, mirroring web-common's StaffRoles / PlatformRoles on the backend.
 *
 *   RK TECHNOLOGIES (tenant `platform`)   PLATFORM_OWNER · PLATFORM_ADMIN · PLATFORM_SUPPORT
 *        └── each tenant (CivEngMarket, …) TENANT_OWNER · ADMIN · SUB_ADMIN · REGIONAL_ADMIN
 *
 * These only decide what the UI offers. Every gate is enforced again server-side, where a platform
 * role also has to be signed in on the `platform` tenant to count.
 */

export const OPERATOR_TENANT = 'platform';

export const PLATFORM_ROLES = ['PLATFORM_OWNER', 'PLATFORM_ADMIN', 'PLATFORM_SUPPORT'] as const;
export const TENANT_STAFF_ROLES = ['TENANT_OWNER', 'ADMIN', 'SUB_ADMIN', 'REGIONAL_ADMIN'] as const;
export const OWNER_ROLES = ['TENANT_OWNER', 'PLATFORM_OWNER'] as const;

type Role = string | null | undefined;

const has = (list: readonly string[], role: Role) => !!role && list.includes(role);

export const isPlatformRole = (role: Role) => has(PLATFORM_ROLES, role);
export const isOwnerRole = (role: Role) => has(OWNER_ROLES, role);
export const isTenantStaffRole = (role: Role) => has(TENANT_STAFF_ROLES, role);

/**
 * RK platform staff on the platform console — who may see the Tenants screen, the factory and
 * cross-tenant figures. An unknown workspace (still loading) is treated as the console, as the
 * backend's fallback host used to be; the server refuses anything else anyway.
 */
export const isPlatformOperator = (role: Role, tenantKey?: string | null) =>
  isPlatformRole(role) && (tenantKey ?? OPERATOR_TENANT) === OPERATOR_TENANT;

/** Read-only platform staff: the UI hides write actions from them; the server refuses them anyway. */
export const isReadOnlyRole = (role: Role) => role === 'PLATFORM_SUPPORT';

const LABELS: Record<string, string> = {
  PLATFORM_OWNER: 'Platform owner',
  PLATFORM_ADMIN: 'Platform admin',
  PLATFORM_SUPPORT: 'Platform support',
  TENANT_OWNER: 'Tenant owner',
};

/** "SITE_ENGINEER" -> "Site engineer", with proper names for the tier roles. */
export const roleLabel = (role: string) =>
  LABELS[role] ?? role.charAt(0) + role.slice(1).toLowerCase().replace(/_/g, ' ');
