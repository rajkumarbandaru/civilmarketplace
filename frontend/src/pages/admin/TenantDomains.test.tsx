import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/tenantApi', async (orig) => ({
  ...(await orig<typeof import('../../services/tenantApi')>()),
  fetchDomains: vi.fn(), addDomain: vi.fn(), checkDomain: vi.fn(), removeDomain: vi.fn(),
}));

import * as api from '../../services/tenantApi';
import DomainsCard from './TenantDomains';

const mocked = vi.mocked(api);
const records: api.DnsRecord[] = [
  { type: 'TXT', name: '_platform-verify.www.acme.test', value: 'tok123', purpose: 'Proves you control the name' },
  { type: 'CNAME', name: 'www.acme.test', value: 'edge.civilengineer.com', purpose: 'Sends its traffic to the platform' },
];
const domain = (overrides: Partial<api.TenantDomain>): api.TenantDomain => ({
  id: 1, tenantKey: 'acme', host: 'www.acme.test', surface: 'WEB', status: 'PENDING_VERIFICATION', records,
  certIssuer: null, certNotAfter: null, attempts: 2, lastError: 'Waiting for TXT _platform-verify.www.acme.test',
  lastCheckedAt: null, activatedAt: null, createdAt: '2026-09-25T10:00:00', ...overrides,
});
const renderCard = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <DomainsCard tenant={{ tenantKey: 'acme', name: 'Acme' } as api.Tenant} />
  </QueryClientProvider>,
);

describe('DomainsCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('adds a domain and shows the DNS records the tenant must publish', async () => {
    mocked.fetchDomains.mockResolvedValueOnce([]).mockResolvedValue([domain({})]);
    mocked.addDomain.mockResolvedValue(domain({}));
    renderCard();
    await userEvent.type(await screen.findByLabelText('Domain'), ' www.acme.test ');
    await userEvent.click(screen.getByRole('button', { name: 'Add domain' }));
    expect(mocked.addDomain).toHaveBeenCalledWith('acme', 'www.acme.test');
    const row = await screen.findByTestId('domain-www.acme.test');
    expect(within(row).getByText('Waiting for DNS')).toBeInTheDocument();
    expect(within(row).getByTestId('record-TXT')).toHaveTextContent('tok123');
    expect(within(row).getByTestId('record-CNAME')).toHaveTextContent('edge.civilengineer.com');
  });

  it('shows a live domain with its certificate and no DNS instructions, and removes it', async () => {
    mocked.fetchDomains.mockResolvedValue([domain({ status: 'ACTIVE', certNotAfter: '2026-12-24T10:00:00', lastError: null })]);
    mocked.removeDomain.mockResolvedValue(domain({ status: 'REMOVED' }));
    renderCard();
    const row = await screen.findByTestId('domain-www.acme.test');
    expect(within(row).getByText('Live')).toBeInTheDocument();
    expect(row).toHaveTextContent('certificate until');
    expect(within(row).queryByTestId('record-TXT')).not.toBeInTheDocument();
    await userEvent.click(within(row).getByRole('button', { name: 'Remove' }));
    expect(mocked.removeDomain).toHaveBeenCalledWith('acme', 1);
  });

  it('explains a degraded domain and lets the operator re-check', async () => {
    mocked.fetchDomains.mockResolvedValue([domain({ status: 'DEGRADED', lastError: 'The TXT record is gone; serving for 7 more days' })]);
    mocked.checkDomain.mockResolvedValue(domain({ status: 'ACTIVE' }));
    renderCard();
    const row = await screen.findByTestId('domain-www.acme.test');
    expect(within(row).getByText('DNS check failing')).toBeInTheDocument();
    expect(row).toHaveTextContent('serving for 7 more days');
    await userEvent.click(within(row).getByRole('button', { name: 'Check now' }));
    expect(mocked.checkDomain).toHaveBeenCalledWith('acme', 1);
  });
});
