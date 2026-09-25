import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/tenantApi', async (orig) => ({
  ...(await orig<typeof import('../../services/tenantApi')>()),
  cryptoShred: vi.fn(),
}));

import * as api from '../../services/tenantApi';
import CryptoShredCard from './CryptoShredCard';

const mocked = vi.mocked(api);
const renderCard = (t: Partial<api.Tenant>) => render(
  <QueryClientProvider client={new QueryClient()}>
    <CryptoShredCard tenant={{ tenantKey: 'oldco', name: 'Old Co', status: 'ARCHIVED', ...t } as api.Tenant} />
  </QueryClientProvider>,
);

describe('CryptoShredCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('is not offered for a tenant that is not archived', () => {
    renderCard({ status: 'ACTIVE' });
    expect(screen.queryByTestId('crypto-shred-card')).not.toBeInTheDocument();
  });

  it('destroys the keys only once the tenant key is typed', async () => {
    mocked.cryptoShred.mockResolvedValue({ tenantKey: 'oldco', keysDestroyed: 2, integrationsDisabled: 1,
      destroyedAt: '2026-09-25T10:00:00' });
    renderCard({});
    await userEvent.click(screen.getByRole('button', { name: 'Destroy encryption keys' }));
    const go = screen.getByRole('button', { name: 'Destroy keys' });
    expect(go).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Tenant key'), 'oldc');
    expect(go).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Tenant key'), 'o');
    await userEvent.click(go);
    expect(mocked.cryptoShred).toHaveBeenCalledWith('oldco', 'oldco');
    expect(await screen.findByTestId('keys-destroyed')).toHaveTextContent('(2 keys)');
  });

  it('shows when the keys were destroyed', () => {
    renderCard({ keysDestroyedAt: '2026-09-25T10:00:00' });
    expect(screen.getByTestId('keys-destroyed')).toHaveTextContent('can no longer be read');
    expect(screen.queryByRole('button', { name: 'Destroy encryption keys' })).not.toBeInTheDocument();
  });
});
