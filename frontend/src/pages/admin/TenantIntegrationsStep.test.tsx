import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { CapabilitySpec, toSaveRequest, missingFields } from '../../services/tenantIntegrationApi';
import TenantIntegrationsStep, {
  changedIntegrations, defaultIntegrationDrafts, incompleteIntegrations, integrationSummary, withoutSecrets,
} from './TenantIntegrationsStep';

const catalog: CapabilitySpec[] = [
  { capability: 'payment', providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret'] } } },
  { capability: 'ai', providers: {
    gemini: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'] },
    openai_compatible: { settings: ['baseUrl', 'model'], secrets: [], optionalSecrets: ['apiKey'] },
  } },
];

describe('integration drafts', () => {
  it('start on the platform account and only save what changed', () => {
    const drafts = defaultIntegrationDrafts(catalog);
    expect(changedIntegrations(drafts)).toEqual([]);
    expect(integrationSummary(drafts.payment)).toBe('Platform account');

    drafts.payment = { ...drafts.payment, mode: 'BYO' };
    drafts.ai = { ...drafts.ai, enabled: false };
    expect(changedIntegrations(drafts).map(([k]) => k)).toEqual(['payment', 'ai']);
    expect(integrationSummary(drafts.ai)).toBe('Off');
  });

  it('flag an own account with missing details, and never keep secrets in the draft', () => {
    const drafts = defaultIntegrationDrafts(catalog);
    drafts.payment = { ...drafts.payment, mode: 'BYO', settings: { keyId: 'rzp' }, secrets: { keySecret: 's3cret' } };
    expect(incompleteIntegrations(catalog, drafts)).toEqual([]);
    drafts.payment = { ...drafts.payment, secrets: {} };
    expect(incompleteIntegrations(catalog, drafts).map((c) => c.capability)).toEqual(['payment']);
    expect(JSON.stringify(withoutSecrets({ ...drafts, x: { ...drafts.payment, secrets: { keySecret: 's3cret' } } })))
      .not.toContain('s3cret');
  });

  it('let an open model be saved without a key, and send the key when given', () => {
    const spec = catalog[1];
    const draft = { mode: 'BYO' as const, provider: 'openai_compatible', enabled: true,
      settings: { baseUrl: 'https://api.groq.com/openai/v1', model: 'llama' }, secrets: {} };
    expect(missingFields(spec, draft)).toEqual([]);
    expect(missingFields(spec, { ...draft, settings: { baseUrl: 'x' } })).toEqual(['model']);
    expect(toSaveRequest(spec, { ...draft, secrets: { apiKey: 'gsk_1' } }).secrets).toEqual({ apiKey: 'gsk_1' });
  });
});

describe('TenantIntegrationsStep', () => {
  it('ties the support and messaging widgets to their modules and the assistant to the AI switch', () => {
    const onModules = vi.fn();
    const onDraft = vi.fn();
    render(<TenantIntegrationsStep catalog={catalog} drafts={defaultIntegrationDrafts(catalog)} onDraft={onDraft}
      modules={new Set(['support', 'messaging'])} onModules={onModules} />);

    fireEvent.click(screen.getByLabelText('In-app messaging'));
    expect([...onModules.mock.calls[0][0]]).toEqual(['support']);

    fireEvent.click(screen.getByLabelText('AI assistant'));
    expect(onDraft).toHaveBeenCalledWith('ai', expect.objectContaining({ enabled: false }));
    expect(screen.getByTestId('integration-payment')).toBeInTheDocument();
    expect(screen.getByTestId('integration-ai')).toBeInTheDocument();
  });
});
