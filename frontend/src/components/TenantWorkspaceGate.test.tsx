import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const state = { role: 'PLATFORM_OWNER', tenantKey: 'platform' };
vi.mock('../hooks', () => ({ useAppSelector: (f: (s: unknown) => unknown) => f({ auth: { user: { role: state.role } } }) }));
vi.mock('../providers/WorkspaceProvider', () => ({ useWorkspace: () => ({ tenantKey: state.tenantKey }) }));
vi.mock('../services/tenantApi', async (original) => ({
  ...(await original<typeof import('../services/tenantApi')>()),
  fetchTenants: vi.fn(),
}));

import * as api from '../services/tenantApi';
import { getActingTenant, setActingTenant } from '../services/actingTenant';
import TenantWorkspaceGate from './TenantWorkspaceGate';

const tenant = (tenantKey: string, name: string, status: api.TenantStatus): api.Tenant => ({
  tenantKey, name, subdomain: tenantKey, customDomain: null, status, contactEmail: null, plan: null,
  vertical: 'CIVIL_MARKETPLACE', modules: [], landingPath: null, branding: null, createdAt: '2026-09-26T00:00:00',
});

const renderGate = () => render(
  <QueryClientProvider client={new QueryClient()}>
    <MemoryRouter initialEntries={['/admin/tenant/users']}>
      <Routes>
        <Route path="/admin/tenant/users" element={<TenantWorkspaceGate><div data-testid="screen">Users screen</div></TenantWorkspaceGate>} />
        <Route path="/admin" element={<div data-testid="home">home</div>} />
        <Route path="/admin/tenants" element={<div data-testid="tenants">tenants</div>} />
      </Routes>
    </MemoryRouter>
  </QueryClientProvider>,
);

describe('TenantWorkspaceGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state.role = 'PLATFORM_OWNER';
    state.tenantKey = 'platform';
    vi.mocked(api.fetchTenants).mockResolvedValue([
      tenant('platform', 'RK Technologies', 'ACTIVE'),
      tenant('civengmarket', 'CivEngMarket', 'ACTIVE'),
      tenant('oldco', 'Old Co', 'SUSPENDED'),
    ]);
  });
  afterEach(() => setActingTenant(null));

  it('asks for a tenant first, offering live customer tenants only', async () => {
    renderGate();
    expect(screen.getByText('Pick a tenant to work in')).toBeInTheDocument();
    expect(screen.queryByTestId('screen')).not.toBeInTheDocument();

    fireEvent.mouseDown(await screen.findByRole('combobox'));
    const options = within(screen.getByRole('listbox')).getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['CivEngMarket (civengmarket)']);
  });

  it('shows the screen once a tenant is picked, and says where changes go', async () => {
    renderGate();
    fireEvent.mouseDown(await screen.findByRole('combobox'));
    fireEvent.click(within(screen.getByRole('listbox')).getByText('CivEngMarket (civengmarket)'));

    expect(await screen.findByTestId('screen')).toBeInTheDocument();
    expect(screen.getByTestId('acting-tenant-name')).toHaveTextContent('Working in CivEngMarket');
    expect(screen.getByTestId('acting-tenant-banner')).toHaveTextContent("Changes here apply to CivEngMarket's live data");
    expect(getActingTenant()).toEqual({ tenantKey: 'civengmarket', name: 'CivEngMarket' });
  });

  it('tells platform support they can only look', async () => {
    state.role = 'PLATFORM_SUPPORT';
    setActingTenant({ tenantKey: 'civengmarket', name: 'CivEngMarket' });
    renderGate();
    expect(await screen.findByTestId('screen')).toBeInTheDocument();
    expect(screen.getByTestId('acting-tenant-banner')).toHaveTextContent('you can look, not change');
  });

  it('leaving clears the tenant and goes back to the tenant list', async () => {
    setActingTenant({ tenantKey: 'civengmarket', name: 'CivEngMarket' });
    renderGate();
    fireEvent.click(await screen.findByRole('button', { name: 'Leave tenant' }));
    expect(await screen.findByTestId('tenants')).toBeInTheDocument();
    expect(getActingTenant()).toBeNull();
  });

  it('a tenant\'s own staff never get in', () => {
    state.role = 'TENANT_OWNER';
    state.tenantKey = 'civengmarket';
    renderGate();
    expect(screen.getByTestId('home')).toBeInTheDocument();
    expect(api.fetchTenants).not.toHaveBeenCalled();
  });
});
