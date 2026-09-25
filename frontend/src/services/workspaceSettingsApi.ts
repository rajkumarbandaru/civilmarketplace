import api from './api';

/**
 * A workspace's own settings, for its own admins (tenant-service `WorkspaceSettingsController`).
 * The server always acts on the signed-in admin's own workspace; nothing here names a tenant.
 */

export interface WorkspaceModule {
  key: string;
  /** The workspace has it switched on. */
  chosen: boolean;
  /** Switched on and in the plan, so it actually runs. */
  running: boolean;
  /** The plan (with add-ons and grants) includes it. */
  entitled: boolean;
  /** Cannot be switched off: sign-in, users, the console and audit. */
  locked: boolean;
}

export interface WorkspaceModules {
  tenantKey: string;
  planName: string;
  modules: WorkspaceModule[];
}

const BASE = '/workspace-settings';

export const fetchWorkspaceModules = async (): Promise<WorkspaceModules> =>
  (await api.get<WorkspaceModules>(`${BASE}/modules`)).data;

/** The optional modules to run; locked ones are always kept by the server. */
export const saveWorkspaceModules = async (modules: string[]): Promise<WorkspaceModules> =>
  (await api.put<WorkspaceModules>(`${BASE}/modules`, { modules })).data;

/** What to send after flipping one module, from the current state. */
export const toggledModules = (current: WorkspaceModule[], key: string, on: boolean): string[] =>
  current
    .filter((m) => !m.locked && (m.key === key ? on : m.chosen))
    .map((m) => m.key);
