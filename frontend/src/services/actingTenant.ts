import { useSyncExternalStore } from 'react';

/**
 * The customer tenant platform staff are working in from the platform console.
 *
 * The console's tenant screens live under {@link TENANT_WORKSPACE_ROOT}; while one is open, calls to
 * the staff screens' APIs carry {@link ACTING_HEADER} and the gateway switches them over to the
 * picked tenant. Everything else — the console's own menu, the bell, the signed-in user's profile —
 * stays on the platform. The gateway enforces the same list (JwtAuthGatewayFilterFactory), so this
 * only keeps the console from asking for what would be refused.
 *
 * Kept per tab (sessionStorage): two tabs can work in two tenants without one switching the other
 * under it.
 */

export const ACTING_HEADER = 'X-Acting-Tenant';
export const TENANT_WORKSPACE_ROOT = '/admin/tenant';

const STORAGE_KEY = 'platform.actingTenant';

export interface ActingTenant {
  tenantKey: string;
  name: string;
}

const read = (): ActingTenant | null => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as ActingTenant) : null;
  } catch {
    return null;
  }
};

let current: ActingTenant | null = read();
const listeners = new Set<() => void>();

export const getActingTenant = (): ActingTenant | null => current;

export const setActingTenant = (tenant: ActingTenant | null): void => {
  current = tenant;
  try {
    if (tenant) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tenant));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage unavailable (private window): the choice still holds for this page's life.
  }
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/** The tenant being worked in, re-rendering when it changes. */
export const useActingTenant = (): ActingTenant | null => useSyncExternalStore(subscribe, getActingTenant);

/** True on the console's tenant-workspace pages. */
export const inTenantWorkspace = (pathname: string): boolean =>
  pathname === TENANT_WORKSPACE_ROOT || pathname.startsWith(`${TENANT_WORKSPACE_ROOT}/`);

/**
 * The console path for a tenant screen: `/admin/users` becomes `/admin/tenant/users` while working
 * in a tenant, so links inside the shared screens stay inside the tenant.
 */
export const tenantScreenPath = (path: string, pathname: string): string =>
  inTenantWorkspace(pathname) && path.startsWith('/admin/') && !inTenantWorkspace(path)
    ? `${TENANT_WORKSPACE_ROOT}/${path.slice('/admin/'.length)}`
    : path;

/** API paths (below /api/v1) the staff screens use. Anything under these may be acted on. */
const STAFF_PREFIXES = ['/admin', '/users/admin', '/workspace-settings'];
/** Read-only extras the staff screens load. */
const STAFF_READS = [/^\/analytics\/workspace$/, /^\/bookings\/[^/]+\/tracking$/, /^\/media\/.+$/];

const onSegment = (path: string, prefix: string) => path === prefix || path.startsWith(`${prefix}/`);

/** Whether a request to `url` (relative to /api/v1) with `method` may carry the acting header. */
export const isActingEligible = (url: string, method: string | undefined): boolean => {
  const path = url.split('?')[0].replace(/^https?:\/\/[^/]+/, '').replace(/^\/api\/v1/, '');
  if (STAFF_PREFIXES.some((prefix) => onSegment(path, prefix))) return true;
  const read = !method || ['get', 'head', 'options'].includes(method.toLowerCase());
  return read && STAFF_READS.some((pattern) => pattern.test(path));
};

/** The acting header for a request, or null: a tenant picked, a tenant page open, a staff API. */
export const actingTenantFor = (url: string, method: string | undefined, pathname: string): string | null => {
  const tenant = current;
  if (!tenant || !inTenantWorkspace(pathname)) return null;
  return isActingEligible(url, method) ? tenant.tenantKey : null;
};
