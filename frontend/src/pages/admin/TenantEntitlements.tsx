import React, { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, FormControlLabel, MenuItem, Stack, Table, TableBody, TableCell, TableRow, TextField, Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  Tenant, addGrant, changePlan, fetchPlanCatalog, fetchTenantEntitlements, formatLimit, moduleLabel,
  previewPlanChange, revokeGrant,
} from '../../services/tenantApi';

const SUB_STATUS_COLOR: Record<string, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
  TRIALING: 'info', ACTIVE: 'success', PAST_DUE: 'warning', SUSPENDED: 'error', CANCELED: 'default',
};

/** Grants last at most a year (enforced server-side too): the latest date the picker offers. */
const maxGrantDate = () => {
  const d = new Date();
  d.setMonth(d.getMonth() + 12);
  return d.toISOString().slice(0, 10);
};

/**
 * What a tenant bought and what it therefore runs: plan, add-ons, limits and grants, with plan
 * changes previewed before they happen. Nothing is deleted when a module stops — the tenant's
 * choice and data are kept, and an upgrade brings them back.
 */
const PlanCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const { formatDateTime } = useDateTime();
  const [changing, setChanging] = useState(false);
  const [planKey, setPlanKey] = useState('');
  const [addOns, setAddOns] = useState<string[]>([]);
  const [granting, setGranting] = useState(false);
  const [grant, setGrant] = useState({ feature: '', limitValue: '', expiresAt: '', reason: '' });

  const catalog = useQuery({ queryKey: ['plan-catalog'], queryFn: fetchPlanCatalog, staleTime: 5 * 60_000, retry: false });
  const current = useQuery({
    queryKey: ['entitlements', tenant.tenantKey],
    queryFn: () => fetchTenantEntitlements(tenant.tenantKey),
    retry: false,
  });
  const preview = useQuery({
    queryKey: ['plan-preview', tenant.tenantKey, planKey, addOns.join(',')],
    queryFn: () => previewPlanChange(tenant.tenantKey, planKey, addOns),
    enabled: changing && planKey !== '',
    retry: false,
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['entitlements', tenant.tenantKey] });
    queryClient.invalidateQueries({ queryKey: ['tenants'] });
  };
  const change = useMutation({
    mutationFn: () => changePlan(tenant.tenantKey, planKey, addOns),
    onSuccess: () => { setChanging(false); refresh(); },
  });
  const addGrantMutation = useMutation({
    mutationFn: () => addGrant(tenant.tenantKey, {
      feature: grant.feature,
      limitValue: grant.limitValue === '' ? null : Number(grant.limitValue),
      expiresAt: `${grant.expiresAt}T23:59:00`,
      reason: grant.reason.trim(),
    }),
    onSuccess: () => { setGranting(false); setGrant({ feature: '', limitValue: '', expiresAt: '', reason: '' }); refresh(); },
  });
  const revoke = useMutation({ mutationFn: (id: number) => revokeGrant(tenant.tenantKey, id), onSuccess: refresh });

  const grantable = useMemo(() => {
    if (!catalog.data) return [];
    const modules = new Set(catalog.data.plans.flatMap((p) => p.features));
    return [
      ...[...modules].sort().map((m) => ({ key: m, label: moduleLabel(m), limit: false })),
      ...Object.entries(catalog.data.limits).map(([k, label]) => ({ key: k, label, limit: true })),
    ];
  }, [catalog.data]);

  if (tenant.tenantKey === 'platform') return null;
  if (current.isLoading) return <CircularProgress size={24} sx={{ mb: 3 }} />;
  if (current.isError || !current.data) {
    return <Alert severity="error" sx={{ mb: 3 }}>{apiErrorMessage(current.error, 'Could not load the plan.')}</Alert>;
  }
  const { entitlements: e, chosenModules, runningModules } = current.data;
  const dormant = chosenModules.filter((m) => !runningModules.includes(m));
  const limitLabels = catalog.data?.limits ?? {};
  const isLimitGrant = grantable.find((g) => g.key === grant.feature)?.limit ?? false;

  return (
    <Card sx={{ mb: 3 }} data-testid="plan-card">
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="h6">Plan</Typography>
            <Chip label={`${e.planName} v${e.planVersion}`} color="primary" />
            <Chip size="small" label={e.status} color={SUB_STATUS_COLOR[e.status]} variant="outlined" />
          </Stack>
          <Button size="small" variant="outlined" onClick={() => {
            setPlanKey(e.planKey); setAddOns(e.addOns); setChanging(true);
          }}>Change plan</Button>
        </Stack>

        {e.addOns.length > 0 && (
          <Stack direction="row" spacing={0.5} sx={{ mb: 1 }} useFlexGap flexWrap="wrap">
            {e.addOns.map((a) => (
              <Chip key={a} size="small" label={catalog.data?.addOns.find((x) => x.key === a)?.name ?? a} />
            ))}
          </Stack>
        )}

        <Table size="small" sx={{ mb: 2, maxWidth: 420 }}>
          <TableBody>
            {Object.entries(limitLabels).map(([k, label]) => (
              <TableRow key={k}>
                <TableCell sx={{ pl: 0 }}>{label}</TableCell>
                <TableCell data-testid={`limit-${k}`}>{formatLimit(e.limits[k])}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {dormant.length > 0 && (
          <Alert severity="info" sx={{ mb: 2 }} data-testid="dormant-modules">
            Not in this plan, so not running: <strong>{dormant.map(moduleLabel).join(', ')}</strong>. The tenant's choice
            and data are kept; upgrading or adding them brings them straight back.
          </Alert>
        )}

        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="subtitle2">Grants</Typography>
          <Button size="small" onClick={() => setGranting(true)}>Add grant</Button>
        </Stack>
        {e.grants.length === 0 && <Typography variant="body2" color="text.secondary">No grants.</Typography>}
        <Stack spacing={0.5}>
          {e.grants.map((g) => (
            <Stack key={g.id} direction="row" spacing={1} alignItems="center" data-testid={`grant-${g.id}`}
              sx={{ opacity: g.active ? 1 : 0.55 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {limitLabels[g.feature] ? `${limitLabels[g.feature]} = ${formatLimit(g.limitValue)}` : moduleLabel(g.feature)}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
                until {formatDateTime(g.expiresAt)} · {g.reason}
              </Typography>
              {g.active
                ? <Button size="small" color="error" onClick={() => revoke.mutate(g.id)}>Revoke</Button>
                : <Chip size="small" label="Ended" variant="outlined" />}
            </Stack>
          ))}
        </Stack>
      </CardContent>

      <Dialog open={changing} onClose={() => setChanging(false)} fullWidth maxWidth="sm">
        <DialogTitle>Change {tenant.name}'s plan</DialogTitle>
        <DialogContent>
          <TextField select fullWidth label="Plan" value={planKey} onChange={(ev) => setPlanKey(ev.target.value)} sx={{ mt: 1, mb: 2 }}>
            {catalog.data?.plans.map((p) => <MenuItem key={p.key} value={p.key}>{p.name} (v{p.version})</MenuItem>)}
          </TextField>
          <Typography variant="subtitle2">Add-ons</Typography>
          {catalog.data?.addOns.map((a) => (
            <FormControlLabel key={a.key} label={a.name} control={
              <Checkbox checked={addOns.includes(a.key)}
                onChange={(ev) => setAddOns(ev.target.checked ? [...addOns, a.key] : addOns.filter((x) => x !== a.key))} />
            } />
          ))}
          <Box sx={{ mt: 2 }} data-testid="plan-impact">
            {preview.isLoading && <CircularProgress size={20} />}
            {preview.data && (
              <>
                {preview.data.modulesStopping.length > 0 && (
                  <Alert severity="warning" sx={{ mb: 1 }}>
                    Stops running: <strong>{preview.data.modulesStopping.map(moduleLabel).join(', ')}</strong>. Their pages
                    return 404 and leave the menu; the data is kept.
                  </Alert>
                )}
                {preview.data.modulesResuming.length > 0 && (
                  <Alert severity="success" sx={{ mb: 1 }}>
                    Starts running again: <strong>{preview.data.modulesResuming.map(moduleLabel).join(', ')}</strong>.
                  </Alert>
                )}
                {Object.entries(preview.data.limitChanges).map(([k, [from, to]]) => (
                  <Typography key={k} variant="body2">
                    {limitLabels[k] ?? k}: {formatLimit(from)} → <strong>{formatLimit(to)}</strong>
                  </Typography>
                ))}
                {preview.data.modulesStopping.length === 0 && preview.data.modulesResuming.length === 0
                  && Object.keys(preview.data.limitChanges).length === 0 && (
                  <Typography variant="body2" color="text.secondary">No change to what runs or to limits.</Typography>
                )}
              </>
            )}
          </Box>
          {change.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(change.error, 'The plan could not be changed.')}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setChanging(false)}>Cancel</Button>
          <Button variant="contained" disabled={!planKey || change.isPending} onClick={() => change.mutate()}>
            {change.isPending ? 'Changing…' : 'Change plan'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={granting} onClose={() => setGranting(false)} fullWidth maxWidth="xs">
        <DialogTitle>Add a grant</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField select label="Feature or limit" value={grant.feature}
              onChange={(ev) => setGrant({ ...grant, feature: ev.target.value, limitValue: '' })}>
              {grantable.map((g) => <MenuItem key={g.key} value={g.key}>{g.label}</MenuItem>)}
            </TextField>
            {isLimitGrant && (
              <TextField label="New limit" type="number" value={grant.limitValue}
                onChange={(ev) => setGrant({ ...grant, limitValue: ev.target.value })} />
            )}
            <TextField label="Until" type="date" InputLabelProps={{ shrink: true }} value={grant.expiresAt}
              inputProps={{ max: maxGrantDate() }} helperText="At most 12 months ahead."
              onChange={(ev) => setGrant({ ...grant, expiresAt: ev.target.value })} />
            <TextField label="Reason" value={grant.reason} onChange={(ev) => setGrant({ ...grant, reason: ev.target.value })}
              inputProps={{ maxLength: 500 }} />
            {addGrantMutation.isError && (
              <Alert severity="error">{apiErrorMessage(addGrantMutation.error, 'The grant was refused.')}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setGranting(false)}>Cancel</Button>
          <Button variant="contained"
            disabled={!grant.feature || !grant.expiresAt || !grant.reason.trim() || (isLimitGrant && grant.limitValue === '')
              || addGrantMutation.isPending}
            onClick={() => addGrantMutation.mutate()}>
            Grant
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

export default PlanCard;
