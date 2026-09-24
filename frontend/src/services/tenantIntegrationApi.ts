import api from './api';
import { API_ORIGIN } from './apiBase';

/**
 * Client for a tenant's provider accounts (tenant-service `TenantIntegrationController`).
 *
 * Operator-only, like the rest of tenantApi. Secrets are write-only: the server returns masked
 * hints of which ones are set and never their values, and a secret left blank on save keeps the
 * stored one — so the form can change a from-address without making the operator re-type a key
 * it never showed them.
 */

export type IntegrationMode = 'BYO' | 'PLATFORM_SHARED';

export interface ProviderSpec {
  settings: string[];
  secrets: string[];
}

export interface CapabilitySpec {
  capability: string;
  /** Mail and AI may run on the platform's account; payment, SMS and WhatsApp may not. */
  allowsPlatformShared: boolean;
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

export const CAPABILITY_LABELS: Record<string, { label: string; help: string }> = {
  payment: {
    label: 'Payments',
    help: "Must be the tenant's own merchant account: customers' money settles to it.",
  },
  email: { label: 'Email', help: 'Sent with the tenant\'s from-name, on their account or the platform\'s.' },
  sms: { label: 'SMS', help: "Needs the tenant's own DLT-registered sender ID." },
  whatsapp: { label: 'WhatsApp', help: "Tied to the tenant's own WhatsApp Business number." },
  ai: { label: 'AI assistant', help: 'Support chat answers. May run on the platform key.' },
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
};

/** `fromAddress` → "From address", with a hand-written label where the split reads badly. */
export const fieldLabel = (key: string) =>
  FIELD_LABELS[key] ??
  key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()).trim();

export const providerLabel = (key: string) =>
  ({ razorpay: 'Razorpay', smtp: 'SMTP', brevo: 'Brevo', twilio: 'Twilio', gemini: 'Google Gemini' })[
    key
  ] ?? key;

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
    mode: configuredMode ?? 'BYO',
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
  const pick = (source: Record<string, string>, keys: string[]) =>
    Object.fromEntries(
      keys.map((k) => [k, source[k]?.trim() ?? ''] as const).filter(([, v]) => v !== '')
    );
  return {
    mode: 'BYO',
    provider: draft.provider,
    enabled: draft.enabled,
    settings: pick(draft.settings, provider.settings),
    secrets: pick(draft.secrets, provider.secrets),
  };
};

/** One-line state for the list row. */
export const integrationStatus = (
  item: TenantIntegration
): { label: string; color: 'success' | 'info' | 'warning' | 'default' } => {
  if (!item.configured) return { label: 'Not configured', color: 'warning' };
  if (!item.enabled) return { label: 'Disabled', color: 'default' };
  if (item.mode === 'PLATFORM_SHARED') return { label: 'Platform account', color: 'info' };
  return { label: `Own account · ${providerLabel(item.provider ?? '')}`, color: 'success' };
};
