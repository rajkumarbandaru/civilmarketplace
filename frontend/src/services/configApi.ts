import api from './api';

/**
 * Theme configuration history (admin-service `ConfigHistoryController`): every save is a release
 * of versioned documents — branding, theme, style, layout — and any earlier release can be made
 * live again. Rollback publishes the old content as a new release; nothing is ever rewritten.
 */

export type ReleaseSource = 'SEED' | 'ONBOARDING' | 'OPERATOR' | 'CONSOLE' | 'ROLLBACK';

export interface ConfigRelease {
  id: number;
  scope: string;
  source: ReleaseSource;
  changeNote: string | null;
  rollbackOfReleaseId: number | null;
  createdBy: number | null;
  createdAt: string;
  documents: string[];
  live: boolean;
}

export interface KeyChange {
  document: string;
  key: string;
  before: unknown;
  after: unknown;
}

export const SOURCE_LABELS: Record<ReleaseSource, string> = {
  SEED: 'Default',
  ONBOARDING: 'Onboarding',
  OPERATOR: 'Platform operator',
  CONSOLE: 'Theme editor',
  ROLLBACK: 'Rollback',
};

/** `scope` is `PLATFORM` for the whole workspace or a role name, as on the theme screen. */
export const fetchReleases = async (scope: string): Promise<ConfigRelease[]> => {
  const { data } = await api.get<ConfigRelease[]>('/admin/config/releases', { params: { scope } });
  return data;
};

export const fetchReleaseDiff = async (releaseId: number): Promise<{ release: ConfigRelease; changes: KeyChange[] }> => {
  const { data } = await api.get(`/admin/config/releases/${releaseId}/diff`);
  return data;
};

export const rollbackRelease = async (releaseId: number, reason: string): Promise<ConfigRelease> => {
  const { data } = await api.post<ConfigRelease>(`/admin/config/releases/${releaseId}/rollback`, { reason });
  return data;
};

/** The workspace's published name and logo, for screens shown before sign-in. */
export const fetchPublicBranding = async (): Promise<{ brandName: string | null; logoUrl: string | null }> => {
  const { data } = await api.get('/ui-config/public/branding');
  return data;
};

/** "primaryColor" → "Primary colour", for the diff table. */
export const settingLabel = (key: string) =>
  ({
    brandName: 'Brand name', logoUrl: 'Logo', mode: 'Colour mode', primaryColor: 'Primary colour',
    accentColor: 'Accent colour', surfaceColor: 'Surface colour', sidebarColor: 'Sidebar colour',
    fontFamily: 'Font', uiStyle: 'UI style', buttonStyle: 'Button style', density: 'Density',
    borderRadius: 'Corner radius', layoutStyle: 'Layout',
  } as Record<string, string>)[key] ?? key;
