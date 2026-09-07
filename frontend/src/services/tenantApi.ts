import api from './api';

/**
 * Client for tenant-service's operator API (`backend/tenant-service`).
 *
 * Every endpoint here is operator-only: tenant-service requires the caller be a SUPER_ADMIN whose
 * `X-Tenant-Id` is the `platform` tenant, so a tenant's own Super Admin gets a 403 rather than the
 * ability to create or suspend a sibling. Both headers come from the gateway's JWT filter, so
 * nothing here sends an identity — the 403 is the authoritative answer, not a client-side check.
 */

export type TenantStatus = 'ACTIVE' | 'PENDING' | 'SUSPENDED' | 'ARCHIVED';
export type Vertical = 'CIVIL_MARKETPLACE' | 'FEE_COLLECTION' | 'PROPERTY';

export const TENANT_STATUSES: TenantStatus[] = ['ACTIVE', 'PENDING', 'SUSPENDED', 'ARCHIVED'];

/** What each status means to traffic, shown next to the control that sets it. */
export const TENANT_STATUS_HELP: Record<TenantStatus, string> = {
  ACTIVE: 'Serving traffic; its schemas exist in every service.',
  PENDING: 'Created but not yet provisioned across services.',
  SUSPENDED: 'Reachable domain, but the gateway refuses requests.',
  ARCHIVED: 'Closed. Schemas are retained; nothing routes here.',
};

export const VERTICALS: Array<{ value: Vertical; label: string; description: string }> = [
  {
    value: 'CIVIL_MARKETPLACE',
    label: 'Civil marketplace',
    description: 'Service marketplace: bookings, projects, reviews, search.',
  },
  {
    value: 'FEE_COLLECTION',
    label: 'Fee collection',
    description: 'Hostels and institutions: residents, fee plans, invoices, collections.',
  },
  {
    value: 'PROPERTY',
    label: 'Property',
    description: 'Bhoomi360: properties, listings, leases, valuations, land records.',
  },
];

/**
 * Mirrors `PlatformModule`. Grouped the way the enum is, because the horizontal set is not a
 * meaningful choice — every tenant has it whatever vertical it runs — and showing it mixed in
 * with the vertical modules invites an operator to switch off `auth`.
 */
export const HORIZONTAL_MODULES = [
  'auth', 'users', 'payments', 'notifications', 'support', 'admin', 'audit', 'messaging',
] as const;

export const VERTICAL_MODULES: Record<Vertical, string[]> = {
  CIVIL_MARKETPLACE: ['bookings', 'projects', 'reviews', 'search'],
  FEE_COLLECTION: ['residents', 'feeplans', 'invoices', 'collections'],
  PROPERTY: ['search', 'reviews', 'properties', 'listings', 'leases', 'valuations', 'landrecords'],
};

/** Every module key the platform knows, for a tenant whose modules have been hand-edited. */
export const ALL_MODULES: string[] = [
  ...HORIZONTAL_MODULES,
  ...Array.from(new Set(Object.values(VERTICAL_MODULES).flat())),
];

/** "feeplans" -> "Fee plans". The enum key is stable API; the wording here is not. */
const MODULE_LABELS: Record<string, string> = {
  feeplans: 'Fee plans',
  landrecords: 'Land records',
};

export const moduleLabel = (key: string) =>
  MODULE_LABELS[key] || key.charAt(0).toUpperCase() + key.slice(1);

/** Mirrors `TenantBranding` in tenant-common. Every field optional — a tenant that sets none
 *  starts on the shipped platform theme. */
export interface TenantBranding {
  logoUrl?: string | null;
  primaryColor?: string | null;
  accentColor?: string | null;
  surfaceColor?: string | null;
  sidebarColor?: string | null;
  colorMode?: string | null;
  uiStyle?: string | null;
  buttonStyle?: string | null;
  layoutStyle?: string | null;
  density?: string | null;
  borderRadius?: number | null;
  fontFamily?: string | null;
  /** The wordmark in the tenant's shell. Blank falls back to the tenant's name. */
  brandName?: string | null;
  /** Which preset this palette started from. Console bookkeeping — nothing renders from it. */
  presetKey?: string | null;
}

/** The closed sets tenant-common validates against; an unlisted value is a 400 on create. */
export const COLOR_MODES = ['light', 'dark', 'system'];
export const UI_STYLES = ['default', 'flat', 'elevated'];
export const BUTTON_STYLES = ['gradient', 'solid', 'outlined'];
export const LAYOUT_STYLES = ['sidebar-left', 'sidebar-right', 'topbar'];
export const DENSITIES = ['compact', 'comfortable', 'spacious'];

/** What each style choice actually changes, shown under its control. */
export const STYLE_HELP: Record<string, string> = {
  default: 'The shipped look — subtle borders and shadows.',
  flat: 'No shadows; surfaces separated by borders alone.',
  elevated: 'Pronounced shadows; cards lift off the background.',
  gradient: 'Contained buttons filled with a gradient.',
  solid: 'Contained buttons filled with one flat colour.',
  outlined: 'Contained buttons drawn as an outline.',
  'sidebar-left': 'Navigation down the left edge.',
  'sidebar-right': 'Navigation down the right edge.',
  topbar: 'Navigation across the top.',
  compact: 'Tighter spacing; more on screen.',
  comfortable: 'The shipped spacing.',
  spacious: 'Looser spacing; easier to scan.',
  light: 'Always light.',
  dark: 'Always dark.',
  system: "Follows each viewer's own device setting.",
};

/**
 * A shipped theme preset, as `/admin/theme/presets` returns it.
 *
 * Fetched rather than hardcoded so the operator console offers exactly the palettes the platform
 * ships — a copy here would drift the moment a preset is added, and the operator would be choosing
 * from a list the tenants themselves no longer see.
 */
export interface ThemePreset {
  key: string;
  label: string;
  description: string | null;
  builtIn: boolean;
  values: {
    mode?: string | null;
    primaryColor?: string | null;
    accentColor?: string | null;
    surfaceColor?: string | null;
    sidebarColor?: string | null;
    borderRadius?: number | null;
    fontFamily?: string | null;
    uiStyle?: string | null;
    buttonStyle?: string | null;
    layoutStyle?: string | null;
    density?: string | null;
  };
}

export const fetchThemePresets = async (): Promise<ThemePreset[]> => {
  const { data } = await api.get<ThemePreset[]>('/admin/theme/presets');
  return data;
};

/**
 * A preset's values as tenant branding.
 *
 * `logoUrl` and `brandName` are deliberately preserved from what the operator already typed: they
 * identify the customer rather than style them, and no preset carries either — so applying a
 * palette must not wipe the wordmark someone just entered.
 */
export const brandingFromPreset = (
  preset: ThemePreset,
  current: TenantBranding
): TenantBranding => ({
  ...current,
  colorMode: preset.values.mode ?? current.colorMode,
  primaryColor: preset.values.primaryColor ?? current.primaryColor,
  accentColor: preset.values.accentColor ?? current.accentColor,
  surfaceColor: preset.values.surfaceColor ?? current.surfaceColor,
  sidebarColor: preset.values.sidebarColor ?? current.sidebarColor,
  borderRadius: preset.values.borderRadius ?? current.borderRadius,
  fontFamily: preset.values.fontFamily ?? current.fontFamily,
  uiStyle: preset.values.uiStyle ?? current.uiStyle,
  buttonStyle: preset.values.buttonStyle ?? current.buttonStyle,
  layoutStyle: preset.values.layoutStyle ?? current.layoutStyle,
  density: preset.values.density ?? current.density,
  presetKey: preset.key,
});

/** Mirrors `TenantMenuOverride` in tenant-common. */
export interface TenantMenuOverride {
  itemKey: string;
  /** False hides the item. */
  visible?: boolean | null;
  /** Replaces the catalogue label for this tenant. */
  labelOverride?: string | null;
  /** Replaces the catalogue position. */
  sortOrder?: number | null;
}

/** `#RRGGBB` or `#RRGGBBAA`, matching what tenant-common accepts. */
export const HEX_COLOR = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;

/** Mirrors `TenantResponse`. */
export interface Tenant {
  tenantKey: string;
  name: string;
  subdomain: string | null;
  customDomain: string | null;
  status: TenantStatus;
  contactEmail: string | null;
  plan: string | null;
  vertical: Vertical;
  modules: string[];
  /** Absent on the list endpoint, which does not query them — see `TenantController`. */
  menuOverrides?: TenantMenuOverride[] | null;
  landingPath: string | null;
  branding: TenantBranding | null;
  createdAt: string;
}

/** Mirrors `CreateTenantRequest`; optional fields are omitted rather than sent empty. */
export interface CreateTenantCommand {
  tenantKey: string;
  name: string;
  contactEmail: string;
  subdomain?: string;
  plan?: string;
  vertical?: Vertical;
  modules?: string[];
  branding?: TenantBranding;
  menuOverrides?: TenantMenuOverride[];
  landingPath?: string | null;
  customDomain?: string;
}

/**
 * Mirrors `UpdateTenantRequest`. Identity only: modules, navigation and branding each have their
 * own call, because each carries a different warning the console has to show before it lands.
 *
 * No `tenantKey` — it names the tenant's schema in every service database, so changing it would be
 * a migration rather than an edit.
 */
export interface UpdateTenantCommand {
  name: string;
  subdomain: string;
  contactEmail: string;
  customDomain?: string;
  plan?: string;
  vertical?: Vertical;
}

const BASE = '/tenants';

export const fetchTenants = async (): Promise<Tenant[]> => {
  const { data } = await api.get<Tenant[]>(BASE);
  return data;
};

export const fetchTenant = async (tenantKey: string): Promise<Tenant> => {
  const { data } = await api.get<Tenant>(`${BASE}/${tenantKey}`);
  return data;
};

export const createTenant = async (command: CreateTenantCommand): Promise<Tenant> => {
  const { data } = await api.post<Tenant>(BASE, command);
  return data;
};

export const updateTenant = async (
  tenantKey: string,
  command: UpdateTenantCommand
): Promise<Tenant> => {
  const { data } = await api.put<Tenant>(`${BASE}/${tenantKey}`, command);
  return data;
};

export const changeTenantStatus = async (
  tenantKey: string,
  status: TenantStatus
): Promise<Tenant> => {
  const { data } = await api.patch<Tenant>(`${BASE}/${tenantKey}/status`, { status });
  return data;
};

/**
 * One menu item and the module it needs. `requiredModule` null means horizontal — Dashboard,
 * Profile, Settings — present for every tenant whatever it bought.
 */
export interface MenuCatalogueEntry {
  itemKey: string;
  label: string;
  path: string;
  icon: string;
  section: string;
  menuGroup: string | null;
  sortOrder: number;
  requiredModule: string | null;
  defaultRoles: string;
}

export const fetchMenuCatalogue = async (): Promise<MenuCatalogueEntry[]> => {
  const { data } = await api.get<MenuCatalogueEntry[]>('/admin/menu-catalogue');
  return data;
};

/**
 * The sidebar a tenant would actually get: catalogue minus items whose module is off, minus items
 * the operator hid, with their labels and ordering applied. Mirrors
 * `UiConfigService.tenantCatalogue()` — the two must agree, or the preview promises a menu the
 * tenant does not receive.
 */
export const previewMenu = (
  catalogue: MenuCatalogueEntry[],
  modules: string[],
  overrides: TenantMenuOverride[]
): MenuCatalogueEntry[] => {
  const enabled = new Set(modules);
  const byKey = new Map(overrides.map((o) => [o.itemKey, o]));
  return catalogue
    .filter((item) => item.requiredModule === null || enabled.has(item.requiredModule))
    .filter((item) => byKey.get(item.itemKey)?.visible !== false)
    .map((item) => {
      const override = byKey.get(item.itemKey);
      if (!override) return item;
      return {
        ...item,
        label: override.labelOverride || item.label,
        sortOrder: override.sortOrder ?? item.sortOrder,
      };
    })
    .sort((a, b) => a.sortOrder - b.sortOrder);
};

/** True when this override says nothing the catalogue does not already say — the server drops these. */
export const isNoopOverride = (override: TenantMenuOverride) =>
  override.visible !== false &&
  !override.labelOverride &&
  (override.sortOrder === null || override.sortOrder === undefined);

/**
 * Replaces the tenant's navigation: hidden items, renamed labels, order, and landing page.
 *
 * One call for all four because they are decided on one screen, and a landing page pointing at an
 * item the same save just hid is not a state worth being able to reach — the server rejects it.
 */
export const setTenantNavigation = async (
  tenantKey: string,
  menuOverrides: TenantMenuOverride[],
  landingPath: string | null,
  /**
   * The catalogue key of the landing page. Sent for validation only — tenant-service holds no menu
   * catalogue, so it cannot tell on its own whether the landing page is one of the items being
   * hidden in the same save.
   */
  landingItemKey?: string | null
): Promise<Tenant> => {
  const { data } = await api.put<Tenant>(`${BASE}/${tenantKey}/navigation`, {
    menuOverrides: menuOverrides.filter((o) => !isNoopOverride(o)),
    landingPath,
    landingItemKey: landingItemKey ?? null,
  });
  return data;
};

/**
 * Whether a tenant has saved its own theme, so the console can warn before replacing it.
 * Served by admin-service, not tenant-service — the theme lives in the tenant's own schema.
 */
export interface TenantThemeStatus {
  tenantKey: string;
  version: number;
  customised: boolean;
}

export const fetchTenantThemeStatus = async (
  tenantKey: string
): Promise<TenantThemeStatus> => {
  const { data } = await api.get<TenantThemeStatus>(`/admin/tenant-theme/${tenantKey}`);
  return data;
};

/** Replaces the tenant's branding and re-pushes it to their console, overwriting their theme. */
export const setTenantBranding = async (
  tenantKey: string,
  branding: TenantBranding
): Promise<Tenant> => {
  const { data } = await api.put<Tenant>(`${BASE}/${tenantKey}/branding`, branding);
  return data;
};

export const setTenantModules = async (
  tenantKey: string,
  modules: string[]
): Promise<Tenant> => {
  const { data } = await api.put<Tenant>(`${BASE}/${tenantKey}/modules`, { modules });
  return data;
};
