import { describe, expect, it } from 'vitest';
import {
  CapabilitySpec,
  TenantIntegration,
  draftFrom,
  fieldLabel,
  integrationStatus,
  missingFields,
  toSaveRequest,
} from './tenantIntegrationApi';

const email: CapabilitySpec = {
  capability: 'email',
  allowsPlatformShared: true,
  providers: {
    smtp: { settings: ['host', 'port', 'username', 'fromAddress', 'fromName'], secrets: ['password'] },
    brevo: { settings: ['fromAddress', 'fromName'], secrets: ['apiKey'] },
  },
};
const payment: CapabilitySpec = {
  capability: 'payment',
  allowsPlatformShared: false,
  providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret', 'webhookSecret'] } },
};

const stored = (over: Partial<TenantIntegration>): TenantIntegration => ({
  capability: 'payment', configured: true, mode: 'BYO', provider: 'razorpay', enabled: true,
  settings: { keyId: 'rzp_test_1' }, secretHints: { keySecret: '••••abcd', webhookSecret: '••••' },
  webhookPath: '/webhooks/payments/razorpay/t', updatedBy: '1', updatedAt: null, ...over,
});

describe('tenant integration helpers', () => {
  it('seeds an empty form for an unconfigured capability', () => {
    const d = draftFrom(payment, { ...stored({}), configured: false, mode: null, provider: null, settings: {} });
    expect(d).toEqual({ mode: 'BYO', provider: 'razorpay', enabled: true, settings: {}, secrets: {} });
  });

  it('seeds settings but never secrets from what is stored', () => {
    const d = draftFrom(payment, stored({}));
    expect(d.settings).toEqual({ keyId: 'rzp_test_1' });
    expect(d.secrets).toEqual({});
  });

  it('requires every setting and secret for a new own-account setup', () => {
    const d = draftFrom(payment);
    expect(missingFields(payment, d)).toEqual(['keyId', 'keySecret', 'webhookSecret']);
  });

  it('lets stored secrets stay blank, but not after switching provider', () => {
    const current = stored({ capability: 'email', provider: 'smtp', settings: {}, secretHints: { password: '••••' } });
    const same = { mode: 'BYO' as const, provider: 'smtp', enabled: true, secrets: {},
      settings: { host: 'h', port: '587', username: 'u', fromAddress: 'a@b.c', fromName: 'A' } };
    expect(missingFields(email, same, current)).toEqual([]);
    expect(missingFields(email, { ...same, provider: 'brevo' }, current)).toEqual(['apiKey']);
  });

  it('needs nothing on the platform account', () => {
    expect(missingFields(email, { ...draftFrom(email), mode: 'PLATFORM_SHARED' })).toEqual([]);
  });

  it('sends only the chosen provider\'s fields, trimmed, and drops blank secrets', () => {
    const req = toSaveRequest(email, {
      mode: 'BYO', provider: 'brevo', enabled: true,
      settings: { fromAddress: ' a@b.c ', fromName: 'Acme', host: 'left-over' },
      secrets: { apiKey: '', password: 'stale' },
    });
    expect(req).toEqual({ mode: 'BYO', provider: 'brevo', enabled: true,
      settings: { fromAddress: 'a@b.c', fromName: 'Acme' }, secrets: {} });
  });

  it('platform account carries no provider or credentials', () => {
    const req = toSaveRequest(email, { mode: 'PLATFORM_SHARED', provider: 'smtp', enabled: false,
      settings: { host: 'x' }, secrets: { password: 'y' } });
    expect(req).toEqual({ mode: 'PLATFORM_SHARED', provider: null, enabled: false, settings: {}, secrets: {} });
  });

  it('describes each state', () => {
    expect(integrationStatus(stored({ configured: false })).label).toBe('Not configured');
    expect(integrationStatus(stored({ enabled: false })).label).toBe('Disabled');
    expect(integrationStatus(stored({ mode: 'PLATFORM_SHARED' })).label).toBe('Platform account');
    expect(integrationStatus(stored({})).label).toBe('Own account · Razorpay');
  });

  it('labels fields readably', () => {
    expect(fieldLabel('accountSid')).toBe('Account SID');
    expect(fieldLabel('someNewField')).toBe('Some New Field');
  });
});
