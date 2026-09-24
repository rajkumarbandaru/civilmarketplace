import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/tenantIntegrationApi', async (orig) => ({
  ...(await orig<typeof import('../../services/tenantIntegrationApi')>()),
  fetchIntegrationCatalog: vi.fn(),
  fetchTenantIntegrations: vi.fn(),
  saveTenantIntegration: vi.fn(),
  deleteTenantIntegration: vi.fn(),
}));

import * as apiModule from '../../services/tenantIntegrationApi';
import TenantIntegrationsCard from './TenantIntegrationsCard';

const mocked = vi.mocked(apiModule);

const catalog: apiModule.CapabilitySpec[] = [
  { capability: 'payment', allowsPlatformShared: false,
    providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret', 'webhookSecret'] } } },
  { capability: 'email', allowsPlatformShared: true,
    providers: { brevo: { settings: ['fromAddress', 'fromName'], secrets: ['apiKey'] } } },
];
const none = (capability: string): apiModule.TenantIntegration => ({ capability, configured: false, mode: null, provider: null, enabled: false,
  settings: {}, secretHints: {}, webhookPath: null, updatedBy: null, updatedAt: null });

const renderCard = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TenantIntegrationsCard tenant={{ tenantKey: 'acme', name: 'Acme Builders' }} />
    </QueryClientProvider>
  );

describe('TenantIntegrationsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocked.fetchIntegrationCatalog.mockResolvedValue(catalog);
    mocked.fetchTenantIntegrations.mockResolvedValue([
      { ...none('payment'), configured: true, mode: 'BYO', provider: 'razorpay', enabled: true,
        settings: { keyId: 'rzp_live_X' }, secretHints: { keySecret: '••••9f2a', webhookSecret: '••••' },
        webhookPath: '/webhooks/payments/razorpay/tok123' },
      { ...none('email'), configured: true, mode: 'PLATFORM_SHARED', enabled: true },
    ]);
  });

  it('shows each capability\'s state, masked secrets and the webhook URL', async () => {
    renderCard();
    const payment = await screen.findByTestId('integration-payment');
    expect(within(payment).getByText('Own account · Razorpay')).toBeInTheDocument();
    expect(within(payment).getByText(/Key ID: rzp_live_X/)).toHaveTextContent('Key secret: ••••9f2a');
    expect(within(payment).getByText(/\/webhooks\/payments\/razorpay\/tok123/)).toBeInTheDocument();
    expect(within(screen.getByTestId('integration-email')).getByText('Platform account')).toBeInTheDocument();
  });

  it('refuses to save a new account until every field is filled, then sends only what was typed', async () => {
    mocked.fetchTenantIntegrations.mockResolvedValue([none('payment'), none('email')]);
    mocked.saveTenantIntegration.mockResolvedValue(none('email'));
    renderCard();
    await userEvent.click(within(await screen.findByTestId('integration-email')).getByRole('button', { name: 'Set up' }));
    await userEvent.click(screen.getByLabelText("Use the tenant's own account"));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(screen.getAllByText('Required')).toHaveLength(3);
    expect(mocked.saveTenantIntegration).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText(/From address/), 'hello@acme.in');
    await userEvent.type(screen.getByLabelText(/From name/), 'Acme');
    await userEvent.type(screen.getByLabelText(/API key/), 'xkeysib-secret');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(mocked.saveTenantIntegration).toHaveBeenCalledWith('acme', 'email', {
      mode: 'BYO', provider: 'brevo', enabled: true,
      settings: { fromAddress: 'hello@acme.in', fromName: 'Acme' }, secrets: { apiKey: 'xkeysib-secret' },
    });
  });

  it('keeps stored secrets when editing other fields', async () => {
    mocked.saveTenantIntegration.mockResolvedValue(none('payment'));
    renderCard();
    await userEvent.click(within(await screen.findByTestId('integration-payment')).getByRole('button', { name: 'Edit' }));
    expect(screen.queryByLabelText(/platform's account/)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Key secret/)).toHaveValue('');
    expect(screen.getByLabelText(/Key secret/)).toHaveAttribute('placeholder', '••••9f2a — leave blank to keep');
    const keyId = screen.getByLabelText(/Key ID/);
    await userEvent.clear(keyId);
    await userEvent.type(keyId, 'rzp_live_Y');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(mocked.saveTenantIntegration).toHaveBeenCalledWith('acme', 'payment', {
      mode: 'BYO', provider: 'razorpay', enabled: true, settings: { keyId: 'rzp_live_Y' }, secrets: {},
    });
  });

  it('removes after confirmation', async () => {
    mocked.deleteTenantIntegration.mockResolvedValue(undefined);
    renderCard();
    await userEvent.click(within(await screen.findByTestId('integration-payment')).getByRole('button', { name: 'Remove' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' }));
    expect(mocked.deleteTenantIntegration).toHaveBeenCalledWith('acme', 'payment');
  });
});
