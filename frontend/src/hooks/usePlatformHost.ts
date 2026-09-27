import { useQuery } from '@tanstack/react-query';
import { lookupWorkspace } from '../services/workspaceApi';
import { OPERATOR_TENANT } from '../utils/roles';

/**
 * Whether this app was opened on the RK Technologies platform host (the `platform` tenant) rather
 * than a tenant's own address, and whether that is still being worked out.
 *
 * Shares WorkspaceProvider's query (same key and options), so this adds no request. `loading` lets
 * a public page wait instead of flashing the marketplace home at RK's address for a moment.
 */
export const usePlatformHost = (): { isPlatform: boolean; loading: boolean } => {
  const { data, isLoading } = useQuery({
    queryKey: ['workspace'], queryFn: lookupWorkspace, staleTime: Infinity, retry: false,
  });
  return {
    isPlatform: data?.kind === 'ok' && data.workspace.tenantKey === OPERATOR_TENANT,
    loading: isLoading,
  };
};

/** What the platform is called before its own name is known (or on a tenant's address). */
export const DEFAULT_PLATFORM_NAME = 'Platform';

/**
 * The platform company's name — "RK Technologies" today — everywhere the UI says it.
 *
 * Never written into the code: it is the platform tenant's own published brand name, so renaming
 * the company is one edit in the platform console (Branding & theme, or the tenant's name) and every
 * page follows. On a tenant's address the platform's name is not part of the page, so this returns
 * the generic word instead of guessing.
 */
export const usePlatformName = (): string => {
  const { data } = useQuery({
    queryKey: ['workspace'], queryFn: lookupWorkspace, staleTime: Infinity, retry: false,
  });
  if (data?.kind === 'ok' && data.workspace.tenantKey === OPERATOR_TENANT) {
    return data.workspace.branding?.brandName || data.workspace.name || DEFAULT_PLATFORM_NAME;
  }
  return DEFAULT_PLATFORM_NAME;
};
