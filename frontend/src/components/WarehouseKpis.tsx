import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert, Box, Card, CardContent, Chip, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { useAppSelector } from '../hooks';
import { useWorkspace } from '../providers/WorkspaceProvider';
import { apiErrorMessage } from '../services/apiError';
import { fetchCaptureStatus, fetchPlatformKpis, fetchWorkspaceKpis, TenantKpis } from '../services/analyticsApi';

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

const Stat: React.FC<{ label: string; value: string | number; testId?: string }> = ({ label, value, testId }) => (
  <Box sx={{ minWidth: 140 }}>
    <Typography variant="caption" color="text.secondary">{label}</Typography>
    <Typography variant="h6" data-testid={testId}>{value}</Typography>
  </Box>
);

const Totals: React.FC<{ k: TenantKpis }> = ({ k }) => (
  <Stack direction="row" spacing={3} useFlexGap flexWrap="wrap" sx={{ mb: 2 }}>
    <Stat label="Bookings" value={k.bookings} testId="kpi-bookings" />
    <Stat label="Last 30 days" value={k.bookingsLast30Days} />
    <Stat label="Cancelled" value={k.cancelledBookings} />
    <Stat label="Customers" value={k.distinctCustomers} />
    <Stat label="Payments received" value={inr.format(k.paymentsCompleted)} testId="kpi-paid" />
    <Stat label="Purchase orders" value={`${k.purchaseOrders} · ${inr.format(k.purchaseOrderValue)}`} />
  </Stack>
);

/**
 * Figures from the analytics warehouse, kept current by change-data capture. The operator sees every
 * tenant and how capture is doing on each cluster; a tenant's staff see their own workspace only.
 */
const WarehouseKpis: React.FC = () => {
  const role = useAppSelector((s) => s.auth.user?.role);
  const workspace = useWorkspace();
  const operator = role === 'SUPER_ADMIN' && (workspace?.tenantKey ?? 'platform') === 'platform';
  const platform = useQuery({ queryKey: ['wh-platform'], queryFn: fetchPlatformKpis, enabled: operator, retry: false, refetchInterval: 10000 });
  const capture = useQuery({ queryKey: ['wh-capture'], queryFn: fetchCaptureStatus, enabled: operator, retry: false, refetchInterval: 10000 });
  const mine = useQuery({ queryKey: ['wh-workspace'], queryFn: fetchWorkspaceKpis, enabled: !operator, retry: false, refetchInterval: 10000 });
  const error = platform.error || capture.error || mine.error;

  return (
    <Card sx={{ mb: 4 }} data-testid="warehouse-kpis">
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="h6">{operator ? 'All workspaces' : 'This workspace'}</Typography>
          <Chip size="small" variant="outlined" label="Live from the warehouse" />
          {capture.data?.map((c) => (
            <Chip key={c.clusterId} size="small" color={c.connected ? 'success' : 'error'}
              label={`${c.clusterId}: ${c.connected ? 'capturing' : 'disconnected'} · ${c.events} changes`}
              data-testid={`capture-${c.clusterId}`} />
          ))}
        </Stack>
        {error && <Alert severity="warning">{apiErrorMessage(error, 'Warehouse figures are unavailable.')}</Alert>}
        {mine.data && (
          <>
            <Totals k={mine.data.totals} />
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={4}>
              {[['By status', mine.data.bookingsByStatus], ['Top cities', mine.data.bookingsByCity]].map(([title, rows]) => (
                <Box key={title as string} sx={{ minWidth: 220 }}>
                  <Typography variant="subtitle2">{title as string}</Typography>
                  {(rows as { label: string; count: number }[]).map((r) => (
                    <Typography key={r.label} variant="body2">{r.label}: {r.count}</Typography>
                  ))}
                </Box>
              ))}
            </Stack>
          </>
        )}
        {platform.data && (
          <>
            <Totals k={platform.data.totals} />
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" data-testid="warehouse-tenants">
                <TableHead>
                  <TableRow>
                    <TableCell>Workspace</TableCell><TableCell align="right">Bookings</TableCell>
                    <TableCell align="right">Last 30 days</TableCell><TableCell align="right">Customers</TableCell>
                    <TableCell align="right">Payments</TableCell><TableCell align="right">Purchase orders</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {platform.data.tenants.map((t) => (
                    <TableRow key={t.tenantKey}>
                      <TableCell>{t.tenantKey}</TableCell>
                      <TableCell align="right">{t.bookings}</TableCell>
                      <TableCell align="right">{t.bookingsLast30Days}</TableCell>
                      <TableCell align="right">{t.distinctCustomers}</TableCell>
                      <TableCell align="right">{inr.format(t.paymentsCompleted)}</TableCell>
                      <TableCell align="right">{t.purchaseOrders}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </CardContent>
    </Card>
  );
};

export default WarehouseKpis;
