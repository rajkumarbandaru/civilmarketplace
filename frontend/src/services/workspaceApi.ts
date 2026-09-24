import api from './api';
import { fetchPublicBranding } from './configApi';

/** The workspace (tenant) this address serves, as the gateway resolved it from the Host. */
export interface Workspace {
  tenantKey: string;
  name: string;
  status: string;
  vertical: string;
  modules: string[];
  branding?: {
    brandName?: string | null;
    logoUrl?: string | null;
    primaryColor?: string | null;
  } | null;
}

export type WorkspaceLookup =
  | { kind: 'ok'; workspace: Workspace }
  /** The gateway serves no workspace at this address. */
  | { kind: 'unknown'; message: string }
  /** The workspace exists but is suspended or archived. */
  | { kind: 'unavailable'; message: string }
  /** Could not tell (network, tenant-service down): carry on unbranded rather than block the app. */
  | { kind: 'undetermined' };

/**
 * Only the gateway's own answers decide: its 404 reads "No workspace is served at …" and its
 * 503 "This workspace is …". Any other failure is not evidence the workspace is gone.
 */
export const lookupWorkspace = async (): Promise<WorkspaceLookup> => {
  try {
    const { data } = await api.get<Workspace>('/tenant-resolution/current');
    return { kind: 'ok', workspace: await withPublishedBranding(data) };
  } catch (error: any) {
    const status = error?.response?.status;
    const message: string = error?.response?.data?.message ?? '';
    if (status === 404 && message.startsWith('No workspace is served at')) return { kind: 'unknown', message };
    if (status === 503 && message.startsWith('This workspace is')) return { kind: 'unavailable', message };
    return { kind: 'undetermined' };
  }
};

/**
 * The workspace's own published name and logo win over the copy tenant-service holds from
 * onboarding: the workspace may have changed its branding since. If that lookup fails the
 * onboarding copy is still a fair fallback.
 */
const withPublishedBranding = async (workspace: Workspace): Promise<Workspace> => {
  try {
    const published = await fetchPublicBranding();
    return {
      ...workspace,
      branding: {
        ...(workspace.branding ?? {}),
        ...(published.brandName ? { brandName: published.brandName } : {}),
        ...(published.logoUrl ? { logoUrl: published.logoUrl } : {}),
      },
    };
  } catch {
    return workspace;
  }
};
