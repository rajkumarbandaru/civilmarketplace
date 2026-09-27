import React, { useEffect, useLayoutEffect, useReducer, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Typography,
} from '@mui/material';
import { Storefront } from '@mui/icons-material';
import { useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../hooks';
import { useWorkspace } from '../providers/WorkspaceProvider';
import { isPlatformOperator, isReadOnlyRole, roleLabel, OPERATOR_TENANT } from '../utils/roles';
import { fetchTenants, Tenant } from '../services/tenantApi';
import { inTenantWorkspace, setActingTenant, useActingTenant } from '../services/actingTenant';

/**
 * Whose data the query cache holds: a tenant's key while working in one, null for the platform's own.
 * Screens share query keys (['workspace-modules'] is the same key on every tenant), so the cache is
 * dropped whenever this changes rather than risk one tenant's figures showing under another's name.
 */
let cacheScope: string | null = null;

/**
 * Wraps a tenant's own admin screen for platform staff on the platform console.
 *
 * Picks the tenant to work in (live tenants only, never the platform itself), says plainly which
 * tenant every change is going to, and remounts the screen when the tenant changes so nothing from
 * the previous one lingers on it. The gateway does the switching; see services/actingTenant.ts.
 */
const TenantWorkspaceGate: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const role = useAppSelector((state) => state.auth.user?.role);
  const workspace = useWorkspace();
  const acting = useActingTenant();
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<Tenant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const operator = isPlatformOperator(role, workspace?.tenantKey);
  const queryClient = useQueryClient();
  const actingKey = acting?.tenantKey ?? null;

  const [, rerender] = useReducer((n: number) => n + 1, 0);
  // The screen waits until the cache is the picked tenant's, so its first query cannot be answered
  // from the previous tenant's (or the platform's) cache.
  const cacheReady = actingKey === cacheScope;
  useLayoutEffect(() => {
    if (actingKey !== cacheScope) {
      queryClient.removeQueries();
      cacheScope = actingKey;
      rerender();
    }
  }, [actingKey, queryClient]);

  useEffect(() => () => {
    // Leaving the tenant workspace altogether: back to the platform's own data.
    if (!inTenantWorkspace(window.location.pathname) && cacheScope !== null) {
      queryClient.removeQueries();
      cacheScope = null;
    }
  }, [queryClient]);

  useEffect(() => {
    if (!operator) return;
    let cancelled = false;
    fetchTenants()
      .then((all) => {
        if (!cancelled) {
          setTenants(all.filter((t) => t.tenantKey !== OPERATOR_TENANT && t.status === 'ACTIVE'));
        }
      })
      .catch(() => { if (!cancelled) setError('Could not load the tenants.'); });
    return () => { cancelled = true; };
  }, [operator]);

  if (!operator) return <Navigate to="/admin" replace />;

  const pick = (tenantKey: string) => {
    const tenant = tenants?.find((t) => t.tenantKey === tenantKey);
    if (tenant) setActingTenant({ tenantKey: tenant.tenantKey, name: tenant.name });
  };

  const leave = () => {
    setActingTenant(null);
    navigate('/admin/tenants');
  };

  const picker = (
    <FormControl size="small" sx={{ minWidth: 240 }}>
      <InputLabel id="acting-tenant-label">Tenant</InputLabel>
      <Select
        labelId="acting-tenant-label"
        label="Tenant"
        value={acting && tenants?.some((t) => t.tenantKey === acting.tenantKey) ? acting.tenantKey : ''}
        onChange={(e) => pick(String(e.target.value))}
        inputProps={{ 'data-testid': 'acting-tenant-select' }}
      >
        {(tenants ?? []).map((t) => (
          <MenuItem key={t.tenantKey} value={t.tenantKey}>{t.name} ({t.tenantKey})</MenuItem>
        ))}
      </Select>
    </FormControl>
  );

  return (
    <Box>
      <Paper
        variant="outlined"
        data-testid="acting-tenant-banner"
        sx={{ p: 2, mb: 3, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', borderColor: 'warning.main' }}
      >
        <Storefront color="warning" />
        <Box sx={{ flex: 1, minWidth: 220 }}>
          {acting ? (
            <>
              <Typography fontWeight={700} data-testid="acting-tenant-name">Working in {acting.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                {isReadOnlyRole(role)
                  ? `${roleLabel(role ?? '')}: you can look, not change.`
                  : `As ${roleLabel(role ?? '')}. Changes here apply to ${acting.name}'s live data.`}
              </Typography>
            </>
          ) : (
            <Typography fontWeight={700}>Pick a tenant to work in</Typography>
          )}
        </Box>
        {tenants === null && !error ? <CircularProgress size={20} /> : picker}
        {acting && <Button variant="outlined" color="inherit" onClick={leave}>Leave tenant</Button>}
      </Paper>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {acting && cacheReady ? <React.Fragment key={acting.tenantKey}>{children}</React.Fragment> : null}
    </Box>
  );
};

export default TenantWorkspaceGate;
