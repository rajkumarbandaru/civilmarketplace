import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/tenantApi', async (orig) => ({
  ...(await orig<typeof import('../../services/tenantApi')>()),
  fetchPlacement: vi.fn(), fetchClusters: vi.fn(), fetchMoves: vi.fn(), startMove: vi.fn(), rollbackMove: vi.fn(),
  dropMoveSource: vi.fn(),
}));

import * as api from '../../services/tenantApi';
import PlacementCard from './TenantPlacement';

const mocked = vi.mocked(api);
const tenant = { tenantKey: 'acme', name: 'Acme Builders' } as api.Tenant;
const clusters: api.DbCluster[] = [
  { clusterId: 'cluster-a', cell: 'cell-1', host: 'mysql', port: 3306, kind: 'SHARED', status: 'ACTIVE', capacity: 500, tenants: 9 },
  { clusterId: 'cluster-b', cell: 'cell-1', host: 'mysql-b', port: 3306, kind: 'SHARED', status: 'ACTIVE', capacity: 500, tenants: 0 },
  { clusterId: 'cluster-d', cell: 'cell-2', host: 'mysql-d', port: 3306, kind: 'DEDICATED', status: 'ACTIVE', capacity: 1, tenants: 1 },
];
const move = (overrides: Partial<api.TenantMove>): api.TenantMove => ({
  id: 4, tenantKey: 'acme', sourceClusterId: 'cluster-a', targetClusterId: 'cluster-b', step: 'DONE', lastError: null,
  schemasCopied: 13, tablesCopied: 140, rowsCopied: 5230, tablesResynced: 2, acknowledged: [], waitingFor: [],
  freezeMillis: 4200, report: { tablesVerified: 140, mismatches: [] }, requestedBy: '1', startedAt: '2026-09-25T10:00:00',
  finishedAt: '2026-09-25T10:00:30', sourceDroppedAt: null, ...overrides,
});
const renderCard = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <PlacementCard tenant={tenant} />
  </QueryClientProvider>,
);

describe('PlacementCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocked.fetchClusters.mockResolvedValue(clusters);
    mocked.fetchMoves.mockResolvedValue([]);
  });

  it('moves a tenant to another cluster after confirmation, never onto a full dedicated one', async () => {
    mocked.fetchPlacement.mockResolvedValue({ tenantKey: 'acme', clusterId: 'cluster-a', cell: 'cell-1', tier: 'STANDARD',
      epoch: 0, status: 'ACTIVE', currentMove: null });
    mocked.startMove.mockResolvedValue(move({ step: 'COPY' }));
    renderCard();
    expect(await screen.findByTestId('placement-cluster')).toHaveTextContent('cluster-a');
    await userEvent.click(screen.getByLabelText('Move to'));
    expect(screen.getByRole('option', { name: /cluster-d/ })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.queryByRole('option', { name: /cluster-a/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: /cluster-b/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Move' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('read-only while the last changes are caught up');
    await userEvent.click(screen.getByRole('button', { name: 'Start move' }));
    expect(mocked.startMove).toHaveBeenCalledWith('acme', 'cluster-b');
  });

  it('shows a finished move with what it copied and how long writes were paused, and offers to drop the old copy', async () => {
    mocked.fetchPlacement.mockResolvedValue({ tenantKey: 'acme', clusterId: 'cluster-b', cell: 'cell-1', tier: 'STANDARD',
      epoch: 1, status: 'ACTIVE', currentMove: move({}) });
    mocked.dropMoveSource.mockResolvedValue(move({ sourceDroppedAt: '2026-09-25T11:00:00' }));
    renderCard();
    expect(await screen.findByTestId('move-stats-4')).toHaveTextContent(
      '13 schemas · 140 tables · 5,230 rows copied · 2 re-copied after the pause · 140 tables verified · writes paused 4.2 s');
    await userEvent.click(screen.getByRole('button', { name: 'Drop old copy on cluster-a' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Drop' }));
    expect(mocked.dropMoveSource).toHaveBeenCalledWith('acme', 4);
  });

  it('offers a rollback when a move failed after the switch, with writes still paused', async () => {
    mocked.fetchPlacement.mockResolvedValue({ tenantKey: 'acme', clusterId: 'cluster-b', cell: 'cell-1', tier: 'STANDARD',
      epoch: 1, status: 'MAINTENANCE', currentMove: move({ step: 'FAILED', acknowledged: ['user-service'],
        lastError: 'Not routing to cluster-b yet: booking-service', freezeMillis: 240000 }) });
    mocked.rollbackMove.mockResolvedValue(move({ step: 'ROLLED_BACK' }));
    renderCard();
    expect(await screen.findByText('Writes paused')).toBeInTheDocument();
    expect(screen.getByText(/booking-service/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Move to')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Roll back to cluster-a' }));
    expect(mocked.rollbackMove).toHaveBeenCalledWith('acme', 4);
  });
});
