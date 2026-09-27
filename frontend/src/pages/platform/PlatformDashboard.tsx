import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, Skeleton, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, Typography,
} from '@mui/material';
import { AddBusiness, Apartment, Build, CheckCircle, Insights, PauseCircle, WorkspacePremium } from '@mui/icons-material';
import { useAppSelector } from '../../hooks';
import { apiErrorMessage } from '../../services/apiError';
import { fetchDrafts, fetchTenants, Tenant, TENANT_STATUS_COLOR } from '../../services/tenantApi';
import { isReadOnlyRole, OPERATOR_TENANT, roleLabel } from '../../utils/roles';
import { usePlatformName } from '../../hooks/usePlatformHost';

const SETTING_UP = ['DRAFT', 'PROVISIONING', 'PROVISIONING_FAILED'];

/** The platform's figures from the tenant list: every tenant but the platform itself. */
export const summarise = (tenants: Tenant[]) => {
  const customers = tenants.filter((t) => t.tenantKey !== OPERATOR_TENANT);
  const byPlan = customers.reduce<Record<string, number>>((acc, t) => {
    const plan = t.plan || 'none';
    acc[plan] = (acc[plan] ?? 0) + 1;
    return acc;
  }, {});
  return {
    total: customers.length,
    active: customers.filter((t) => t.status === 'ACTIVE').length,
    settingUp: customers.filter((t) => SETTING_UP.includes(t.status)).length,
    paused: customers.filter((t) => t.status === 'SUSPENDED' || t.status === 'ARCHIVED').length,
    byPlan,
    recent: [...customers].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 8),
  };
};

const Stat: React.FC<{ label: string; value: number | undefined; icon: React.ReactNode; color: string; testId: string }> = ({
  label, value, icon, color, testId,
}) => (
  <Card sx={{ borderRadius: 3, height: '100%' }}>
    <CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Box>
          <Typography variant="body2" color="text.secondary">{label}</Typography>
          {value === undefined
            ? <Skeleton width={48} height={40} />
            : <Typography variant="h4" sx={{ fontWeight: 800 }} data-testid={testId}>{value}</Typography>}
        </Box>
        <Box sx={{ color, display: 'flex' }}>{icon}</Box>
      </Stack>
    </CardContent>
  </Card>
);

/**
 * The RK console's home: how the tenants on the platform are doing, rather than the marketplace
 * dashboard (bookings, revenue, categories) a tenant's own console opens on.
 */
const PlatformDashboard: React.FC = () => {
  const role = useAppSelector((state) => state.auth.user?.role);
  const platformName = usePlatformName();
  const readOnly = isReadOnlyRole(role);
  const tenants = useQuery({ queryKey: ['tenants'], queryFn: fetchTenants, retry: false });
  const drafts = useQuery({ queryKey: ['tenant-drafts'], queryFn: fetchDrafts, retry: false });
  const summary = tenants.data ? summarise(tenants.data) : undefined;

  return (
    <Box data-testid="platform-dashboard">
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ md: 'center' }}
        spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>Platform overview</Typography>
          <Typography variant="body2" color="text.secondary">
            Every tenant {platformName} runs, signed in as {role ? roleLabel(role).toLowerCase() : 'staff'}.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          {!readOnly && (
            <Button component={RouterLink} to="/admin/tenants/new" variant="contained" startIcon={<AddBusiness />}>
              New tenant
            </Button>
          )}
          <Button component={RouterLink} to="/admin/plans" variant="outlined" startIcon={<WorkspacePremium />}>
            Plans
          </Button>
          <Button component={RouterLink} to="/admin/platform-analytics" variant="outlined" startIcon={<Insights />}>
            Analytics
          </Button>
        </Stack>
      </Stack>

      {tenants.isError && (
        <Alert severity="error" sx={{ mb: 3 }}>{apiErrorMessage(tenants.error, 'Could not load the tenants.')}</Alert>
      )}

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={6} md={3}>
          <Stat label="Tenants" value={summary?.total} icon={<Apartment />} color="#4338ca" testId="stat-tenants" />
        </Grid>
        <Grid item xs={6} md={3}>
          <Stat label="Live" value={summary?.active} icon={<CheckCircle />} color="#10b981" testId="stat-active" />
        </Grid>
        <Grid item xs={6} md={3}>
          <Stat label="Being set up" value={summary === undefined ? undefined : summary.settingUp + (drafts.data?.length ?? 0)}
            icon={<Build />} color="#f59e0b" testId="stat-setting-up" />
        </Grid>
        <Grid item xs={6} md={3}>
          <Stat label="Suspended or archived" value={summary?.paused} icon={<PauseCircle />} color="#ef4444" testId="stat-paused" />
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card sx={{ borderRadius: 3 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                <Typography variant="h6">Newest tenants</Typography>
                <Button component={RouterLink} to="/admin/tenants" size="small">Manage tenants</Button>
              </Stack>
              {summary && summary.recent.length === 0 ? (
                <Typography color="text.secondary" sx={{ py: 2 }}>No tenants yet.</Typography>
              ) : (
                <Table size="small" data-testid="platform-recent-tenants">
                  <TableHead>
                    <TableRow>
                      <TableCell>Tenant</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Plan</TableCell>
                      <TableCell>Product</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(summary?.recent ?? []).map((t) => (
                      <TableRow key={t.tenantKey}>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{t.name}</Typography>
                          <Typography variant="caption" color="text.secondary">{t.tenantKey}</Typography>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={t.status.replace(/_/g, ' ').toLowerCase()} color={TENANT_STATUS_COLOR[t.status]} />
                        </TableCell>
                        <TableCell>{t.plan ?? '—'}</TableCell>
                        <TableCell>{t.vertical.replace(/_/g, ' ').toLowerCase()}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ borderRadius: 3, height: '100%' }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>Tenants by plan</Typography>
              {summary && Object.keys(summary.byPlan).length === 0 && (
                <Typography color="text.secondary">No tenants yet.</Typography>
              )}
              <Stack spacing={1} data-testid="platform-plan-breakdown">
                {Object.entries(summary?.byPlan ?? {}).map(([plan, count]) => (
                  <Stack key={plan} direction="row" justifyContent="space-between">
                    <Typography sx={{ textTransform: 'capitalize' }}>{plan}</Typography>
                    <Chip size="small" label={count} />
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default PlatformDashboard;
