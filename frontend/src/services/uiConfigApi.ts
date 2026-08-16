import api from './api';

/**
 * The dynamic UI configuration: the side menu, theme and UI style the backend serves instead of
 * them being compiled into this bundle. Null fields in a theme mean "use the built-in default"
 * — they are not colours chosen by an admin, so the shipped MUI theme stays the base palette.
 */

export interface ResolvedTheme {
  scopeKey: string;
  mode: 'light' | 'dark' | 'system';
  primaryColor: string | null;
  accentColor: string | null;
  surfaceColor: string | null;
  /** The shell's navigation bar — separate from the surface colour. */
  sidebarColor: string | null;
  borderRadius: number | null;
  fontFamily: string | null;
  /** Wordmark in the shell; null keeps the shipped product name. */
  brandName: string | null;
  logoUrl: string | null;
  /** default | flat | elevated */
  uiStyle: string | null;
  /** gradient | solid | outlined */
  buttonStyle: string | null;
  /** sidebar-left | sidebar-right */
  layoutStyle: string | null;
  /** compact | comfortable | spacious */
  density: string | null;
  /** Bumped on every save — the client re-fetches when it changes. */
  version: number;
}

export interface ResolvedMenuItem {
  key: string;
  label: string;
  path: string;
  /** A Material-UI icon name, resolved by components/DynamicIcon. */
  icon: string;
  section: string;
  /** Sub-heading within the section; null when the catalogue has not placed the item in a group. */
  menuGroup: string | null;
  sortOrder: number;
  exactMatch: boolean;
}

export interface UiConfigSnapshot {
  userId: number;
  role: string;
  menu: ResolvedMenuItem[];
  theme: ResolvedTheme;
  /** IANA zone id, or null when the member follows the browser's own zone. */
  timezone: string | null;
  /** A key from `DATE_FORMAT_KEYS`, or null for the site default. */
  dateFormat: string | null;
}

export interface AppearanceSettings {
  scopeKey: string;
  workspaceLabel: string;
  effective: ResolvedTheme;
  /** The member's own choice, or null when they are following the workspace. */
  myColorMode: string | null;
  myDensity: string | null;
  /** The member's own zone, or null when they follow the browser. */
  myTimezone: string | null;
  /** The member's own date layout, or null for the site default. */
  myDateFormat: string | null;
  colorModeOptions: string[];
  densityOptions: string[];
  dateFormatOptions: string[];
  /** Which fields this screen should render controls for. */
  memberEditable: string[];
  /** Which fields it should render as read-only values — Super Admin owns these. */
  adminControlled: string[];
}

export interface WorkspaceSummary {
  role: string;
  label: string;
  userCount: number;
  visibleMenuCount: number;
  menuCustomised: boolean;
  themeCustomised: boolean;
}

export interface WorkspaceCreateCommand {
  /** Normalised to UPPER_SNAKE_CASE by the backend — "Site engineer" becomes SITE_ENGINEER. */
  name: string;
  description?: string;
}

export interface WorkspaceMenuRow {
  itemKey: string;
  label: string;
  defaultLabel: string;
  path: string;
  icon: string;
  section: string;
  sortOrder: number;
  defaultSortOrder: number;
  visible: boolean;
  defaultVisible: boolean;
  customised: boolean;
}

export interface MenuUpdateCommand {
  itemKey: string;
  visible: boolean;
  sortOrder: number | null;
  labelOverride: string | null;
}

export interface ThemeUpdateCommand {
  mode: string;
  primaryColor: string | null;
  accentColor: string | null;
  surfaceColor: string | null;
  sidebarColor: string | null;
  borderRadius: number | null;
  fontFamily: string | null;
  brandName: string | null;
  logoUrl: string | null;
  uiStyle: string | null;
  buttonStyle: string | null;
  layoutStyle: string | null;
  density: string | null;
}

/**
 * A shipped starting point for a theme. Applying one fills the editor's form — it is not saved
 * until the admin presses save, so a preset never becomes a second source of truth.
 */
export interface ThemePreset {
  key: string;
  label: string;
  description: string;
  values: ThemeUpdateCommand;
  /** False for a preset a Super Admin saved — only those can be deleted. */
  builtIn: boolean;
}

// ------------------------------------------------------------------ the signed-in user's shell

export const fetchUiConfig = async (role?: string): Promise<UiConfigSnapshot> => {
  const { data } = await api.get<UiConfigSnapshot>('/ui-config/me', {
    params: role ? { role } : undefined,
  });
  return data;
};

export const fetchMyAppearance = async (): Promise<AppearanceSettings> => {
  const { data } = await api.get<AppearanceSettings>('/ui-config/me/appearance');
  return data;
};

/** A null field means "follow the workspace", so clearing is the same call as setting. */
export const updateMyAppearance = async (
  command: { colorMode: string | null; density: string | null }
): Promise<AppearanceSettings> => {
  const { data } = await api.put<AppearanceSettings>('/ui-config/me/appearance', command);
  return data;
};

export const resetMyAppearance = async (): Promise<AppearanceSettings> => {
  const { data } = await api.delete<AppearanceSettings>('/ui-config/me/appearance');
  return data;
};

// ------------------------------------------------------------------------ Super Admin console

export const fetchPlatformTheme = async (): Promise<ResolvedTheme> => {
  const { data } = await api.get<ResolvedTheme>('/admin/theme');
  return data;
};

export const updatePlatformTheme = async (command: ThemeUpdateCommand): Promise<ResolvedTheme> => {
  const { data } = await api.put<ResolvedTheme>('/admin/theme', command);
  return data;
};

export const fetchThemePresets = async (): Promise<ThemePreset[]> => {
  const { data } = await api.get<ThemePreset[]>('/admin/theme/presets');
  return data;
};

/**
 * Saves the editor's current values as a named preset. Saving under a name that already exists
 * overwrites that preset, so re-saving is how one is corrected.
 */
export const saveThemePreset = async (
  preset: { label: string; description: string | null; values: ThemeUpdateCommand }
): Promise<ThemePreset> => {
  const { data } = await api.post<ThemePreset>('/admin/theme/presets', preset);
  return data;
};

export const deleteThemePreset = async (key: string): Promise<void> => {
  await api.delete(`/admin/theme/presets/${key}`);
};

export const fetchWorkspaces = async (): Promise<WorkspaceSummary[]> => {
  const { data } = await api.get<WorkspaceSummary[]>('/admin/workspaces');
  return data;
};

/** Creating a workspace creates the role behind it, so the name is an identifier, not a label. */
export const createWorkspace = async (
  command: WorkspaceCreateCommand
): Promise<WorkspaceSummary> => {
  const { data } = await api.post<WorkspaceSummary>('/admin/workspaces', command);
  return data;
};

export const fetchWorkspaceMenu = async (role: string): Promise<WorkspaceMenuRow[]> => {
  const { data } = await api.get<WorkspaceMenuRow[]>(`/admin/workspaces/${role}/menu`);
  return data;
};

export const updateWorkspaceMenu = async (
  role: string,
  commands: MenuUpdateCommand[]
): Promise<WorkspaceMenuRow[]> => {
  const { data } = await api.put<WorkspaceMenuRow[]>(`/admin/workspaces/${role}/menu`, commands);
  return data;
};

export const resetWorkspaceMenu = async (role: string): Promise<WorkspaceMenuRow[]> => {
  const { data } = await api.delete<WorkspaceMenuRow[]>(`/admin/workspaces/${role}/menu`);
  return data;
};

/** The workspace's own override, unmerged — nulls here mean "inherit the platform theme". */
export const fetchWorkspaceTheme = async (role: string): Promise<ResolvedTheme> => {
  const { data } = await api.get<ResolvedTheme>(`/admin/workspaces/${role}/theme`);
  return data;
};

/** What the workspace actually paints, i.e. its own row merged over the platform theme. */
export const fetchEffectiveWorkspaceTheme = async (role: string): Promise<ResolvedTheme> => {
  const { data } = await api.get<ResolvedTheme>(`/admin/workspaces/${role}/theme/effective`);
  return data;
};

export const updateWorkspaceTheme = async (
  role: string,
  command: ThemeUpdateCommand
): Promise<ResolvedTheme> => {
  const { data } = await api.put<ResolvedTheme>(`/admin/workspaces/${role}/theme`, command);
  return data;
};

export const resetWorkspaceTheme = async (role: string): Promise<ResolvedTheme> => {
  const { data } = await api.delete<ResolvedTheme>(`/admin/workspaces/${role}/theme`);
  return data;
};
