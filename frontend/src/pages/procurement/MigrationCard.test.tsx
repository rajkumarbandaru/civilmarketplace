import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/procurementApi', async (orig) => ({
  ...(await orig<typeof import('../../services/procurementApi')>()),
  migrateAccounts: vi.fn(),
}));

import * as api from '../../services/procurementApi';
import MigrationCard from './MigrationCard';

const mocked = vi.mocked(api);
const entry = (outcome: api.MigrationEntry['outcome']): api.MigrationEntry => ({
  userId: 20, email: 'supplier@civileng.test', name: 'Deepak Supplier', role: 'MATERIAL_SUPPLIER',
  capabilities: ['SUPPLIER'], outcome, organizationId: outcome === 'CREATED' ? 5 : null, catalogueItems: 3,
});

describe('MigrationCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('previews first, then creates exactly what the preview showed', async () => {
    mocked.migrateAccounts.mockImplementation(async (dryRun) => ({
      dryRun, accounts: 1, created: 1, alreadyMigrated: 0, entries: [entry(dryRun ? 'WOULD_CREATE' : 'CREATED')],
    }));
    render(<QueryClientProvider client={new QueryClient()}><MigrationCard /></QueryClientProvider>);
    expect(screen.getByRole('button', { name: 'Create organizations' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }));
    expect(mocked.migrateAccounts).toHaveBeenLastCalledWith(true);
    expect(await screen.findByTestId('migration-summary')).toHaveTextContent('Preview: 1 account, 1 to create');
    expect(screen.getByText('Will be created')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Create 1 organization' }));
    expect(mocked.migrateAccounts).toHaveBeenLastCalledWith(false);
    expect(await screen.findByText('Created')).toBeInTheDocument();
    expect(screen.getByTestId('migration-summary')).toHaveTextContent('Done: 1 account, 1 created');
  });
});
