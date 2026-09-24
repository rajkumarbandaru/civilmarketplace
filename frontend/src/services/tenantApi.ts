import api from './api';
import manifest from '../experience/registry-manifest.json';

/**
 * Client for tenant-service's operator API (`backend/tenant-service`).
 *
 * Every endpoint here is operator-only: tenant-service requires the caller be a SUPER_ADMIN whose
 * `X-Tenant-Id` is the `platform` tenant, so a tenant's own Super Admin gets a 403 rather than the
 * ability to create or suspend a sibling. Both headers come from the gateway's JWT filter, so
 * nothing here sends an identity — the 403 is the authoritative answer, not a client-side check.
 */

export type TenantStatus =
  | 'DRAFT' | 'PROVISIONING' | 'PROVISIONING_FAILED' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export type Vertical = 'CIVIL_MARKETPLACE' | 'FEE_COLLECTION' | 'PROPERTY';

export const TENANT_STATUSES: TenantStatus[] =
  ['DRAFT', 'PROVISIONING', 'PROVISIONING_FAILED', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'];

/** What an operator may set by hand; the rest belong to publishing and provisioning. */
export const OPERATOR_SETTABLE_STATUSES: TenantStatus[] = ['ACTIVE', 'SUSPENDED', 'ARCHIVED'];

/** What each status means to traffic, shown next to the control that sets it. */
export const TENANT_STATUS_HELP: Record<TenantStatus, string> = {
  DRAFT: 'Created from the wizard: key and subdomain reserved, nothing provisioned, no traffic.',
  PROVISIONING: 'Published: every service is building its storage. Goes live when all are ready.',
  PROVISIONING_FAILED: 'A provisioning step failed. Retry, or discard it.',
  ACTIVE: 'Serving traffic; its schemas exist in every service.',
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
  procurement: 'Procurement (B2B)',
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
// The frontend's experience registry: the only values it can render, so the only ones offered.
export const COLOR_MODES = manifest.colorModes;
export const UI_STYLES = manifest.stylePacks;
export const BUTTON_STYLES = manifest.buttonStyles;
export const LAYOUT_STYLES = manifest.shellLayouts;
export const DENSITIES = manifest.densities;
export const SITE_LAYOUTS = manifest.siteLayouts;

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
  ownerName?: string | null;
  ownerEmail?: string | null;
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

// ------------------------------------------------------------------ Platform Factory

/** The wizard's autosaved state: the create command, the owner, and the form as it was left. */
export interface TenantDraft {
  id: number;
  title: string | null;
  data: Record<string, any>;
  version: number;
  status: string;
  tenantKey: string | null;
  updatedBy: string | null;
  updatedAt: string;
  issues: Array<{ section: string; message: string }>;
}

export const fetchDrafts = async (): Promise<TenantDraft[]> => (await api.get<TenantDraft[]>(`${BASE}/drafts`)).data;

export const createDraft = async (data: object): Promise<TenantDraft> =>
  (await api.post<TenantDraft>(`${BASE}/drafts`, data)).data;

/** Autosave. A 409 means someone else saved this draft after `version` was loaded. */
export const saveDraft = async (id: number, version: number, data: object): Promise<TenantDraft> =>
  (await api.put<TenantDraft>(`${BASE}/drafts/${id}`, { version, data })).data;

export const discardDraft = async (id: number) => { await api.delete(`${BASE}/drafts/${id}`); };

/** Draft → DRAFT tenant: key and subdomain reserved, nothing provisioned yet. */
export const createTenantFromDraft = async (id: number): Promise<Tenant> =>
  (await api.post<Tenant>(`${BASE}/drafts/${id}/create`)).data;

export interface ProvisioningProgress {
  tenantKey: string;
  status: TenantStatus;
  step: 'AWAIT_SCHEMAS' | 'CREATE_OWNER' | 'ACTIVATE' | 'INVITE_OWNER' | 'DONE' | 'FAILED' | null;
  attempts: number;
  lastError: string | null;
  requestedAt: string | null;
  finishedAt: string | null;
  services: Array<{ service: string; state: 'WAITING' | 'READY' | 'FAILED'; error: string | null }>;
}

/** DRAFT (or failed) → provisioning; goes ACTIVE by itself once every service is ready. */
export const publishTenant = async (tenantKey: string): Promise<ProvisioningProgress> =>
  (await api.post<ProvisioningProgress>(`${BASE}/${tenantKey}/publish`)).data;

export const fetchProvisioning = async (tenantKey: string): Promise<ProvisioningProgress> =>
  (await api.get<ProvisioningProgress>(`${BASE}/${tenantKey}/provisioning`)).data;

export const resendOwnerInvitation = async (tenantKey: string) => {
  await api.post(`${BASE}/${tenantKey}/owner-invitation`);
};

/** Only a tenant that never went live (DRAFT, PROVISIONING_FAILED). */
export const discardTenant = async (tenantKey: string) => { await api.delete(`${BASE}/${tenantKey}`); };

// ------------------------------------------------------------------ Entitlements (plans)

export interface PlanView { key: string; version: number; name: string; features: string[]; limits: Record<string, number>; }
export interface AddOn { key: string; name: string; features: string[]; increments: Record<string, number>; }
export interface PlanCatalog { plans: PlanView[]; addOns: AddOn[]; limits: Record<string, string>; baseModules: string[]; }

export interface Grant {
  id: number; feature: string; limitValue: number | null; expiresAt: string; reason: string;
  grantedBy: string | null; active: boolean;
}

export interface Entitlements {
  tenantKey: string; planKey: string; planVersion: number; planName: string;
  status: 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELED';
  addOns: string[]; features: string[]; limits: Record<string, number>; grants: Grant[];
}

export interface TenantEntitlements { entitlements: Entitlements; chosenModules: string[]; runningModules: string[]; }

export interface PlanImpact {
  fromPlan: string; toPlan: string; modulesStopping: string[]; modulesResuming: string[];
  limitChanges: Record<string, [number | null, number | null]>;
}

export const fetchPlanCatalog = async (): Promise<PlanCatalog> => (await api.get<PlanCatalog>(`${BASE}/plans`)).data;

export const fetchTenantEntitlements = async (tenantKey: string): Promise<TenantEntitlements> =>
  (await api.get<TenantEntitlements>(`${BASE}/${tenantKey}/entitlements`)).data;

export const previewPlanChange = async (tenantKey: string, plan: string, addOns: string[]): Promise<PlanImpact> =>
  (await api.get<PlanImpact>(`${BASE}/${tenantKey}/subscription/preview`, {
    params: { plan, addOns: addOns.join(',') || undefined },
  })).data;

export const changePlan = async (tenantKey: string, plan: string, addOns: string[]): Promise<Entitlements> =>
  (await api.put<Entitlements>(`${BASE}/${tenantKey}/subscription`, { plan, addOns })).data;

export const setSubscriptionStatus = async (tenantKey: string, status: Entitlements['status']): Promise<Entitlements> =>
  (await api.put<Entitlements>(`${BASE}/${tenantKey}/subscription/status`, { status })).data;

export const addGrant = async (tenantKey: string, grant: { feature: string; limitValue?: number | null; expiresAt: string; reason: string }) =>
  (await api.post<Entitlements>(`${BASE}/${tenantKey}/grants`, grant)).data;

export const revokeGrant = async (tenantKey: string, grantId: number) =>
  (await api.delete<Entitlements>(`${BASE}/${tenantKey}/grants/${grantId}`)).data;

/** A plan's entitlement as a module set (base modules are always included). */
export const entitledModules = (catalog: PlanCatalog | undefined, planKey: string | undefined): Set<string> | undefined => {
  const plan = catalog?.plans.find((p) => p.key === planKey);
  return plan ? new Set([...catalog!.baseModules, ...plan.features]) : undefined;
};

/** "bookings.monthly" -> 5000, or "Unlimited" when absent. */
export const formatLimit = (value: number | null | undefined) =>
  value === null || value === undefined ? 'Unlimited' : value.toLocaleString();
