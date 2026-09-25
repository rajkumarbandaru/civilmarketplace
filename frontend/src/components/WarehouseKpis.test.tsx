import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const state = { role: 'SUPER_ADMIN', tenantKey: 'platform' };
vi.mock('../hooks', () => ({ useAppSelector: (f: (s: unknown) => unknown) => f({ auth: { user: { role: state.role } } }) }));
vi.mock('../providers/WorkspaceProvider', () => ({ useWorkspace: () => ({ tenantKey: state.tenantKey }) }));
vi.mock('../services/analyticsApi', () => ({
  fetchPlatformKpis: vi.fn(), fetchCaptureStatus: vi.fn(), fetchWorkspaceKpis: vi.fn(),
}));

import * as api from '../services/analyticsApi';
import WarehouseKpis from './WarehouseKpis';

const mocked = vi.mocked(api);
const kpis = (tenantKey: string, bookings: number): api.TenantKpis => ({ tenantKey, bookings, bookingsLast30Days: bookings,
  cancelledBookings: 0, distinctCustomers: 2, paymentsCompleted: 2500, purchaseOrders: 1, purchaseOrderValue: 403840 });
const renderIt = () => render(<QueryClientProvider client={new QueryClient()}><WarehouseKpis /></QueryClientProvider>);

describe('WarehouseKpis', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows the operator every workspace and the capture status of each cluster', async () => {
    Object.assign(state, { role: 'SUPER_ADMIN', tenantKey: 'platform' });
    mocked.fetchPlatformKpis.mockResolvedValue({ totals: kpis('*', 7), tenants: [kpis('acme', 5), kpis('bigco', 2)] });
    mocked.fetchCaptureStatus.mockResolvedValue([{ clusterId: 'cluster-a', host: 'mysql', connected: true,
      binlogFile: 'binlog.000035', binlogPosition: 1, events: 42, lastEventAt: null }]);
    renderIt();
    expect(await screen.findByTestId('warehouse-tenants')).toHaveTextContent('acme');
    expect(screen.getByTestId('kpi-bookings')).toHaveTextContent('7');
    expect(await screen.findByTestId('capture-cluster-a')).toHaveTextContent('capturing · 42 changes');
    expect(mocked.fetchWorkspaceKpis).not.toHaveBeenCalled();
  });

  it('shows a tenant admin only their own workspace, never the cross-tenant view', async () => {
    Object.assign(state, { role: 'ADMIN', tenantKey: 'acme' });
    mocked.fetchWorkspaceKpis.mockResolvedValue({ totals: kpis('acme', 5), bookingsByStatus: [{ label: 'PENDING', count: 5 }],
      bookingsByCity: [{ label: 'Pune', count: 5 }] });
    renderIt();
    expect(screen.getByText('This workspace')).toBeInTheDocument();
    expect(await screen.findByTestId('kpi-bookings')).toHaveTextContent('5');
    expect(screen.getByText('Pune: 5')).toBeInTheDocument();
    expect(mocked.fetchPlatformKpis).not.toHaveBeenCalled();
    expect(mocked.fetchCaptureStatus).not.toHaveBeenCalled();
  });
});
