import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { CapabilitySpec, IntegrationClient, TenantIntegration } from '../../services/tenantIntegrationApi';
import { IntegrationsPanel } from './TenantIntegrationsCard';

const catalog: CapabilitySpec[] = [
  { capability: 'payment',
    providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret', 'webhookSecret'] } } },
  { capability: 'email',
    providers: { brevo: { settings: ['fromAddress', 'fromName'], secrets: ['apiKey'] } } },
  { capability: 'ai',
    providers: {
      gemini: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'] },
      anthropic: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'] },
    } },
];
const none = (capability: string): TenantIntegration => ({ capability, configured: false, mode: null, provider: null, enabled: false,
  settings: {}, secretHints: {}, webhookPath: null, updatedBy: null, updatedAt: null });

const client = {
  key: ['test-integrations'],
  fetchCatalog: vi.fn(),
  fetchIntegrations: vi.fn(),
  save: vi.fn(),
  reset: vi.fn(),
} satisfies IntegrationClient;

const renderPanel = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <IntegrationsPanel name="Acme Builders" client={client} intro="intro" />
    </QueryClientProvider>
  );

describe('IntegrationsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    client.fetchCatalog.mockResolvedValue(catalog);
    client.fetchIntegrations.mockResolvedValue([
      { ...none('payment'), configured: true, mode: 'BYO', provider: 'razorpay', enabled: true,
        settings: { keyId: 'rzp_live_X' }, secretHints: { keySecret: '••••9f2a', webhookSecret: '••••' },
        webhookPath: '/webhooks/payments/razorpay/tok123' },
      { ...none('email'), configured: true, mode: 'PLATFORM_SHARED', enabled: true },
      none('ai'),
    ]);
  });

  it('shows each capability\'s state, masked secrets and the webhook URL; unset ones are on the platform default', async () => {
    renderPanel();
    const payment = await screen.findByTestId('integration-payment');
    expect(within(payment).getByText('Own account · Razorpay')).toBeInTheDocument();
    expect(within(payment).getByText(/Key ID: rzp_live_X/)).toHaveTextContent('Key secret: ••••9f2a');
    expect(within(payment).getByText(/\/webhooks\/payments\/razorpay\/tok123/)).toBeInTheDocument();
    expect(within(screen.getByTestId('integration-email')).getByText('Platform account')).toBeInTheDocument();
    expect(within(screen.getByTestId('integration-ai')).getByText('Platform default')).toBeInTheDocument();
  });

  it('refuses to save an own account until every field is filled, then sends only what was typed', async () => {
    client.fetchIntegrations.mockResolvedValue([none('payment'), none('email'), none('ai')]);
    client.save.mockResolvedValue(none('email'));
    renderPanel();
    await userEvent.click(within(await screen.findByTestId('integration-email')).getByRole('button', { name: 'Change' }));
    expect(screen.getByLabelText(/Use the platform's account/)).toBeChecked();
    await userEvent.click(screen.getByLabelText('Use our own account'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getAllByText('Required')).toHaveLength(3);
    expect(client.save).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText(/From address/), 'hello@acme.in');
    await userEvent.type(screen.getByLabelText(/From name/), 'Acme');
    await userEvent.type(screen.getByLabelText(/API key/), 'xkeysib-secret');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(client.save).toHaveBeenCalledWith('email', {
      mode: 'BYO', provider: 'brevo', enabled: true,
      settings: { fromAddress: 'hello@acme.in', fromName: 'Acme' }, secrets: { apiKey: 'xkeysib-secret' },
    });
  });

  it('lets the AI assistant move to another provider with an optional model', async () => {
    client.save.mockResolvedValue(none('ai'));
    renderPanel();
    await userEvent.click(within(await screen.findByTestId('integration-ai')).getByRole('button', { name: 'Change' }));
    await userEvent.click(screen.getByLabelText('Use our own account'));
    fireEvent.mouseDown(within(screen.getByRole('dialog')).getByText('Google Gemini'));
    await userEvent.click(screen.getByRole('option', { name: 'Anthropic (Claude)' }));
    expect(screen.getByLabelText(/Model \(optional\)/)).toHaveAttribute('placeholder', 'claude-sonnet-5');
    await userEvent.type(screen.getByLabelText(/API key/), 'sk-ant-123');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(client.save).toHaveBeenCalledWith('ai', {
      mode: 'BYO', provider: 'anthropic', enabled: true, settings: {}, secrets: { apiKey: 'sk-ant-123' },
    });
  });

  it('switches a capability off', async () => {
    client.save.mockResolvedValue(none('email'));
    renderPanel();
    await userEvent.click(within(await screen.findByTestId('integration-email')).getByRole('button', { name: 'Change' }));
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(client.save).toHaveBeenCalledWith('email', {
      mode: 'PLATFORM_SHARED', provider: null, enabled: false, settings: {}, secrets: {},
    });
  });

  it('keeps stored secrets when editing other fields', async () => {
    client.save.mockResolvedValue(none('payment'));
    renderPanel();
    await userEvent.click(within(await screen.findByTestId('integration-payment')).getByRole('button', { name: 'Change' }));
    expect(screen.getByLabelText(/Key secret/)).toHaveValue('');
    expect(screen.getByLabelText(/Key secret/)).toHaveAttribute('placeholder', '••••9f2a — leave blank to keep');
    const keyId = screen.getByLabelText(/Key ID/);
    await userEvent.clear(keyId);
    await userEvent.type(keyId, 'rzp_live_Y');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(client.save).toHaveBeenCalledWith('payment', {
      mode: 'BYO', provider: 'razorpay', enabled: true, settings: { keyId: 'rzp_live_Y' }, secrets: {},
    });
  });

  it('goes back to the platform default after confirmation', async () => {
    client.reset.mockResolvedValue(undefined);
    renderPanel();
    await userEvent.click(within(await screen.findByTestId('integration-payment')).getByRole('button', { name: 'Use platform default' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Use platform default' }));
    expect(client.reset).toHaveBeenCalledWith('payment');
  });
});
