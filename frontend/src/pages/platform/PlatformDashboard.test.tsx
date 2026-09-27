import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const state = { role: 'PLATFORM_OWNER' };
vi.mock('../../hooks', () => ({ useAppSelector: (f: (s: unknown) => unknown) => f({ auth: { user: { role: state.role } } }) }));
vi.mock('../../services/tenantApi', async (original) => ({
  ...(await original<typeof import('../../services/tenantApi')>()),
  fetchTenants: vi.fn(),
  fetchDrafts: vi.fn(),
}));

import * as api from '../../services/tenantApi';
import PlatformDashboard, { summarise } from './PlatformDashboard';

const tenant = (tenantKey: string, status: api.TenantStatus, plan: string | null, createdAt: string): api.Tenant => ({
  tenantKey, name: tenantKey.toUpperCase(), subdomain: tenantKey, customDomain: null, status, contactEmail: null, plan,
  vertical: 'CIVIL_MARKETPLACE', modules: [], landingPath: null, branding: null, createdAt,
});
const tenants = [
  tenant('platform', 'ACTIVE', 'enterprise', '2026-01-01T00:00:00'),
  tenant('civengmarket', 'ACTIVE', 'enterprise', '2026-09-26T09:00:00'),
  tenant('acme', 'PROVISIONING', 'professional', '2026-09-26T10:00:00'),
  tenant('oldco', 'SUSPENDED', 'starter', '2026-03-01T00:00:00'),
];

const renderIt = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter><PlatformDashboard /></MemoryRouter>
  </QueryClientProvider>,
);

describe('summarise', () => {
  it('counts every tenant but the platform itself', () => {
    const s = summarise(tenants);
    expect(s).toMatchObject({ total: 3, active: 1, settingUp: 1, paused: 1 });
    expect(s.byPlan).toEqual({ enterprise: 1, professional: 1, starter: 1 });
    expect(s.recent.map((t) => t.tenantKey)).toEqual(['acme', 'civengmarket', 'oldco']);
  });
});

describe('PlatformDashboard', () => {
  beforeEach(() => {
    state.role = 'PLATFORM_OWNER';
    vi.mocked(api.fetchTenants).mockResolvedValue(tenants);
    vi.mocked(api.fetchDrafts).mockResolvedValue([{ id: 1 } as api.TenantDraft]);
  });

  it('shows the platform figures and the newest tenants', async () => {
    renderIt();
    expect(await screen.findByTestId('stat-tenants')).toHaveTextContent('3');
    expect(screen.getByTestId('stat-active')).toHaveTextContent('1');
    // One provisioning tenant plus one open draft.
    expect(screen.getByTestId('stat-setting-up')).toHaveTextContent('2');
    expect(screen.getByTestId('platform-recent-tenants')).toHaveTextContent('CIVENGMARKET');
    expect(screen.getByTestId('platform-recent-tenants')).not.toHaveTextContent('PLATFORM');
    expect(screen.getByRole('link', { name: /New tenant/ })).toHaveAttribute('href', '/admin/tenants/new');
  });

  it('offers read-only support staff no way to create a tenant', async () => {
    state.role = 'PLATFORM_SUPPORT';
    renderIt();
    await screen.findByTestId('stat-tenants');
    expect(screen.queryByRole('link', { name: /New tenant/ })).not.toBeInTheDocument();
  });
});
