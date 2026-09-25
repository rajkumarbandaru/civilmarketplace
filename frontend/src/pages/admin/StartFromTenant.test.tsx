import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/tenantApi', async (orig) => ({
  ...(await orig<typeof import('../../services/tenantApi')>()),
  fetchTenants: vi.fn(),
  fetchTenant: vi.fn(),
}));

import * as api from '../../services/tenantApi';
import { StartFromTenant } from './TenantManagement';

const mocked = vi.mocked(api);
const tenant = (tenantKey: string, over: Partial<api.Tenant> = {}): api.Tenant => ({
  tenantKey, name: tenantKey.toUpperCase(), subdomain: tenantKey, customDomain: null, status: 'ACTIVE',
  contactEmail: null, plan: 'professional', vertical: 'CIVIL_MARKETPLACE', modules: ['auth', 'bookings'],
  landingPath: null, branding: null, createdAt: '2026-09-01T00:00:00', ...over,
});

const renderIt = (onApply = vi.fn()) => {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <StartFromTenant onApply={onApply} />
    </QueryClientProvider>
  );
  return onApply;
};

describe('StartFromTenant', () => {
  beforeEach(() => vi.clearAllMocks());

  it('offers live customer tenants and copies the full record of the one chosen', async () => {
    mocked.fetchTenants.mockResolvedValue([tenant('platform'), tenant('acme'), tenant('old', { status: 'SUSPENDED' })]);
    const full = tenant('acme', { menuOverrides: [{ itemKey: 'reviews', visible: false }], landingPath: '/admin/bookings' });
    mocked.fetchTenant.mockResolvedValue(full);
    const onApply = renderIt();

    fireEvent.mouseDown(within(await screen.findByTestId('start-from-tenant')).getByRole('combobox'));
    expect(screen.queryByRole('option', { name: /platform/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /old/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: 'ACME (acme)' }));

    expect(mocked.fetchTenant).toHaveBeenCalledWith('acme');
    expect(onApply).toHaveBeenCalledWith(full);
    expect(await screen.findByText(/Copied ACME's setup/)).toBeInTheDocument();
  });

  it('stays out of the way when there is nothing to copy', async () => {
    mocked.fetchTenants.mockResolvedValue([tenant('platform')]);
    renderIt();
    await new Promise((r) => setTimeout(r, 0));
    expect(screen.queryByTestId('start-from-tenant')).not.toBeInTheDocument();
  });
});
