import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Divider, Grid, Stack, Typography,
} from '@mui/material';
import { apiErrorMessage } from '../../services/apiError';
import { fetchPlanCatalog, fetchTenants, formatLimit, moduleLabel } from '../../services/tenantApi';
import { OPERATOR_TENANT } from '../../utils/roles';

/**
 * The plans RK sells and which tenants are on each. Read-only here: a tenant's plan is changed on
 * that tenant's page, where the effect on its running modules is previewed first.
 */
const PlansPage: React.FC = () => {
  const catalog = useQuery({ queryKey: ['plan-catalog'], queryFn: fetchPlanCatalog, retry: false });
  const tenants = useQuery({ queryKey: ['tenants'], queryFn: fetchTenants, retry: false });

  if (catalog.isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }
  if (catalog.isError || !catalog.data) {
    return <Alert severity="error">{apiErrorMessage(catalog.error, 'Could not load the plans.')}</Alert>;
  }

  const { plans, addOns, limits, baseModules } = catalog.data;
  const onPlan = (key: string) =>
    (tenants.data ?? []).filter((t) => t.tenantKey !== OPERATOR_TENANT && t.plan === key);

  return (
    <Box data-testid="plans-page">
      <Typography variant="h5" sx={{ fontWeight: 800 }}>Plans & subscriptions</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        What each plan entitles a tenant to. Every plan includes {baseModules.map(moduleLabel).join(', ')}.
        Change a tenant's plan from its page under Tenants.
      </Typography>

      <Grid container spacing={3}>
        {plans.map((plan) => {
          const members = onPlan(plan.key);
          return (
            <Grid item xs={12} md={4} key={`${plan.key}-${plan.version}`}>
              <Card sx={{ borderRadius: 3, height: '100%' }} data-testid={`plan-${plan.key}`}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>{plan.name}</Typography>
                    <Chip size="small" label={`v${plan.version}`} variant="outlined" />
                  </Stack>
                  <Typography variant="overline" color="text.secondary">Modules</Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
                    {plan.features.map((f) => <Chip key={f} size="small" label={moduleLabel(f)} />)}
                  </Stack>
                  <Typography variant="overline" color="text.secondary">Limits</Typography>
                  {Object.entries(limits).map(([key, label]) => (
                    <Stack key={key} direction="row" justifyContent="space-between">
                      <Typography variant="body2">{label}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{formatLimit(plan.limits[key])}</Typography>
                    </Stack>
                  ))}
                  <Divider sx={{ my: 1.5 }} />
                  <Typography variant="overline" color="text.secondary">
                    Tenants on this plan ({members.length})
                  </Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {members.length === 0
                      ? <Typography variant="body2" color="text.secondary">None yet</Typography>
                      : members.map((t) => <Chip key={t.tenantKey} size="small" color="primary" variant="outlined" label={t.name} />)}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          );
        })}
      </Grid>

      {addOns.length > 0 && (
        <Card sx={{ borderRadius: 3, mt: 3 }}>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1 }}>Add-ons</Typography>
            <Stack spacing={0.5}>
              {addOns.map((a) => (
                <Typography key={a.key} variant="body2">
                  <strong>{a.name}</strong>
                  {a.features.length > 0 && ` — adds ${a.features.map(moduleLabel).join(', ')}`}
                  {Object.keys(a.increments).length > 0 && ` — raises ${Object.entries(a.increments)
                    .map(([k, v]) => `${limits[k] ?? k} by ${v.toLocaleString()}`).join(', ')}`}
                </Typography>
              ))}
            </Stack>
          </CardContent>
        </Card>
      )}

      <Button component={RouterLink} to="/admin/tenants" sx={{ mt: 2 }}>Go to tenants</Button>
    </Box>
  );
};

export default PlansPage;
