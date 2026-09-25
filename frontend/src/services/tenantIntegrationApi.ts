import api from './api';
import { API_ORIGIN } from './apiBase';

/**
 * Client for a tenant's provider accounts: payments, email, SMS, WhatsApp and the AI assistant.
 *
 * Every capability runs on the platform's account unless the tenant brings its own (or switches it
 * off). Two ways in: the operator console for any tenant (`TenantIntegrationController`), and a
 * workspace's own admins for theirs (`WorkspaceSettingsController`) — see {@link IntegrationClient}.
 *
 * Secrets are write-only: the server returns masked hints of which ones are set and never their
 * values, and a secret left blank on save keeps the stored one.
 */

export type IntegrationMode = 'BYO' | 'PLATFORM_SHARED';

export interface ProviderSpec {
  settings: string[];
  secrets: string[];
  /** Settings that may be left blank, e.g. the AI model (each provider has a default). */
  optionalSettings?: string[];
}

export interface CapabilitySpec {
  capability: string;
  providers: Record<string, ProviderSpec>;
}

export interface TenantIntegration {
  capability: string;
  configured: boolean;
  mode: IntegrationMode | null;
  provider: string | null;
  enabled: boolean;
  settings: Record<string, string>;
  secretHints: Record<string, string>;
  webhookPath: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface SaveIntegrationRequest {
  mode: IntegrationMode;
  provider: string | null;
  enabled: boolean;
  settings: Record<string, string>;
  secrets: Record<string, string>;
}

const BASE = '/tenants';

export const fetchIntegrationCatalog = async (): Promise<CapabilitySpec[]> => {
  const { data } = await api.get<CapabilitySpec[]>(`${BASE}/integration-catalog`);
  return data;
};

export const fetchTenantIntegrations = async (tenantKey: string): Promise<TenantIntegration[]> => {
  const { data } = await api.get<TenantIntegration[]>(`${BASE}/${tenantKey}/integrations`);
  return data;
};

export const saveTenantIntegration = async (
  tenantKey: string,
  capability: string,
  request: SaveIntegrationRequest
): Promise<TenantIntegration> => {
  const { data } = await api.put<TenantIntegration>(
    `${BASE}/${tenantKey}/integrations/${capability}`,
    request
  );
  return data;
};

export const deleteTenantIntegration = async (tenantKey: string, capability: string) => {
  await api.delete(`${BASE}/${tenantKey}/integrations/${capability}`);
};

/** One place to read and write a tenant's integrations: the operator's, or the workspace's own. */
export interface IntegrationClient {
  /** Cache key for the list. */
  key: string[];
  fetchCatalog: () => Promise<CapabilitySpec[]>;
  fetchIntegrations: () => Promise<TenantIntegration[]>;
  save: (capability: string, request: SaveIntegrationRequest) => Promise<TenantIntegration>;
  /** Back to the platform's account. */
  reset: (capability: string) => Promise<void>;
}

export const operatorIntegrations = (tenantKey: string): IntegrationClient => ({
  key: ['tenant-integrations', tenantKey],
  fetchCatalog: fetchIntegrationCatalog,
  fetchIntegrations: () => fetchTenantIntegrations(tenantKey),
  save: (capability, request) => saveTenantIntegration(tenantKey, capability, request),
  reset: (capability) => deleteTenantIntegration(tenantKey, capability),
});

const SELF = '/workspace-settings';

/** The signed-in admin's own workspace; the server takes the tenant from the session, not the URL. */
export const workspaceIntegrations: IntegrationClient = {
  key: ['workspace-integrations'],
  fetchCatalog: async () => (await api.get<CapabilitySpec[]>(`${SELF}/integration-catalog`)).data,
  fetchIntegrations: async () => (await api.get<TenantIntegration[]>(`${SELF}/integrations`)).data,
  save: async (capability, request) =>
    (await api.put<TenantIntegration>(`${SELF}/integrations/${capability}`, request)).data,
  reset: async (capability) => { await api.delete(`${SELF}/integrations/${capability}`); },
};

export const CAPABILITY_LABELS: Record<string, { label: string; help: string; platformNote: string }> = {
  payment: {
    label: 'Payments',
    help: 'The payment gateway customers pay through.',
    platformNote: "On the platform's account, customers' money settles to the platform, which pays the tenant out.",
  },
  email: {
    label: 'Email',
    help: "Account and booking emails, sent with the tenant's from-name.",
    platformNote: "Sent through the platform's mail account with the tenant's name.",
  },
  sms: {
    label: 'SMS',
    help: 'Text messages such as one-time codes and booking updates.',
    platformNote: "Sent from the platform's DLT-registered sender ID.",
  },
  whatsapp: {
    label: 'WhatsApp',
    help: 'WhatsApp notifications.',
    platformNote: "Sent from the platform's WhatsApp Business number.",
  },
  ai: {
    label: 'AI assistant',
    help: 'The Civil AI Assistant in support chat. Google Gemini by default; OpenAI or Anthropic with the tenant\'s own key.',
    platformNote: "Answered by the platform's Google Gemini account.",
  },
};

export const capabilityLabel = (key: string) => CAPABILITY_LABELS[key]?.label ?? key;

const FIELD_LABELS: Record<string, string> = {
  keyId: 'Key ID',
  keySecret: 'Key secret',
  webhookSecret: 'Webhook secret',
  apiKey: 'API key',
  accountSid: 'Account SID',
  authToken: 'Auth token',
  fromNumber: 'From number',
  senderId: 'Sender ID (DLT)',
  senderName: 'Sender name',
  fromAddress: 'From address',
  fromName: 'From name',
  host: 'SMTP host',
  port: 'SMTP port',
  username: 'Username',
  password: 'Password',
  model: 'Model',
};

/** `fromAddress` → "From address", with a hand-written label where the split reads badly. */
export const fieldLabel = (key: string) =>
  FIELD_LABELS[key] ??
  key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()).trim();

export const providerLabel = (key: string) =>
  ({
    razorpay: 'Razorpay', smtp: 'SMTP', brevo: 'Brevo', twilio: 'Twilio', gemini: 'Google Gemini',
    openai: 'OpenAI', anthropic: 'Anthropic (Claude)',
  } as Record<string, string>)[key] ?? key;

/** What the model box suggests when left blank. */
export const DEFAULT_MODELS: Record<string, string> = {
  gemini: 'gemini-3.6-flash',
  openai: 'gpt-5-mini',
  anthropic: 'claude-sonnet-5',
};

/** The full URL to paste into the provider's dashboard. Relative when the API is same-origin. */
export const webhookUrl = (path: string) =>
  `${API_ORIGIN || (typeof window !== 'undefined' ? window.location.origin : '')}${path}`;

export interface IntegrationDraft {
  mode: IntegrationMode;
  provider: string;
  enabled: boolean;
  settings: Record<string, string>;
  secrets: Record<string, string>;
}

/** Seeds the edit form from what is stored; secret inputs always start empty. */
export const draftFrom = (spec: CapabilitySpec, current?: TenantIntegration): IntegrationDraft => {
  const providers = Object.keys(spec.providers);
  const configuredMode = current?.configured ? current.mode : null;
  return {
    // Nothing stored means the platform's account: that is where the form starts too.
    mode: configuredMode ?? 'PLATFORM_SHARED',
    provider: current?.provider && spec.providers[current.provider] ? current.provider : providers[0] ?? '',
    enabled: current?.configured ? current.enabled : true,
    settings: { ...(current?.settings ?? {}) },
    secrets: {},
  };
};

/**
 * What the form still needs before it can be saved. A secret counts as present if it is typed now
 * or already stored under the same provider (it keeps its value when left blank).
 */
export const missingFields = (
  spec: CapabilitySpec,
  draft: IntegrationDraft,
  current?: TenantIntegration
): string[] => {
  if (draft.mode === 'PLATFORM_SHARED') return [];
  const provider = spec.providers[draft.provider];
  if (!provider) return ['provider'];
  const keepsStored =
    current?.configured && current.mode === 'BYO' && current.provider === draft.provider;
  return [
    ...provider.settings.filter((k) => !draft.settings[k]?.trim()),
    ...provider.secrets.filter(
      (k) => !draft.secrets[k]?.trim() && !(keepsStored && current?.secretHints[k])
    ),
  ];
};

/** Only the fields the chosen provider takes, trimmed; blank secrets are left out to keep them. */
export const toSaveRequest = (spec: CapabilitySpec, draft: IntegrationDraft): SaveIntegrationRequest => {
  if (draft.mode === 'PLATFORM_SHARED') {
    return { mode: 'PLATFORM_SHARED', provider: null, enabled: draft.enabled, settings: {}, secrets: {} };
  }
  const provider = spec.providers[draft.provider] ?? { settings: [], secrets: [] };
  const settingKeys = [...provider.settings, ...(provider.optionalSettings ?? [])];
  const pick = (source: Record<string, string>, keys: string[]) =>
    Object.fromEntries(
      keys.map((k) => [k, source[k]?.trim() ?? ''] as const).filter(([, v]) => v !== '')
    );
  return {
    mode: 'BYO',
    provider: draft.provider,
    enabled: draft.enabled,
    settings: pick(draft.settings, settingKeys),
    secrets: pick(draft.secrets, provider.secrets),
  };
};

/** One-line state for the list row. */
export const integrationStatus = (
  item: TenantIntegration
): { label: string; color: 'success' | 'info' | 'warning' | 'default' } => {
  if (!item.configured) return { label: 'Platform default', color: 'info' };
  if (!item.enabled) return { label: 'Off', color: 'default' };
  if (item.mode === 'PLATFORM_SHARED') return { label: 'Platform account', color: 'info' };
  return { label: `Own account · ${providerLabel(item.provider ?? '')}`, color: 'success' };
};
