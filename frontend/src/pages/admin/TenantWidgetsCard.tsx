import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Card, CardContent, FormControlLabel, Stack, Switch, Typography } from '@mui/material';
import { apiErrorMessage } from '../../services/apiError';
import { Tenant, setTenantModules } from '../../services/tenantApi';
import {
  draftFrom, fetchIntegrationCatalog, fetchTenantIntegrations, saveTenantIntegration, toSaveRequest,
} from '../../services/tenantIntegrationApi';

/**
 * A live tenant's chat widgets, switched in one place: the support chat (the `support` module),
 * in-app messaging (the `messaging` module) and the AI assistant (the AI capability's on/off).
 * The same switches as the new-tenant wizard's, applied immediately.
 */
const TenantWidgetsCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  // The switch moves the moment it is clicked; the server's answer then confirms or reverts it.
  const [pending, setPending] = useState<Record<string, boolean>>({});
  const settle = (key: string) => setPending(({ [key]: _, ...rest }) => rest);
  const catalog = useQuery({ queryKey: ['integration-catalog'], queryFn: fetchIntegrationCatalog, retry: false });
  const integrations = useQuery({
    queryKey: ['tenant-integrations', tenant.tenantKey],
    queryFn: () => fetchTenantIntegrations(tenant.tenantKey),
    retry: false,
  });
  const aiRow = integrations.data?.find((i) => i.capability === 'ai');
  // No row, or a row switched on: the assistant runs (on the platform's account by default).
  const aiOn = !aiRow?.configured || aiRow.enabled;

  const setModule = useMutation({
    mutationFn: ({ key, on }: { key: string; on: boolean }) => {
      const next = new Set(tenant.modules);
      if (on) next.add(key);
      else next.delete(key);
      return setTenantModules(tenant.tenantKey, [...next]);
    },
    onMutate: ({ key, on }) => setPending((p) => ({ ...p, [key]: on })),
    onSettled: async (_data, _error, { key }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['tenants'] }),
        queryClient.invalidateQueries({ queryKey: ['tenants', tenant.tenantKey] }),
      ]);
      settle(key);
    },
  });

  const setAi = useMutation({
    mutationFn: (on: boolean) => {
      const spec = catalog.data?.find((c) => c.capability === 'ai');
      if (!spec) throw new Error('The AI provider list is unavailable.');
      // Only the switch changes: the stored provider, model and key stay as they are.
      return saveTenantIntegration(tenant.tenantKey, 'ai', toSaveRequest(spec, { ...draftFrom(spec, aiRow), enabled: on }));
    },
    onMutate: (on) => setPending((p) => ({ ...p, ai: on })),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tenant-integrations', tenant.tenantKey] });
      settle('ai');
    },
  });

  const error = setModule.error || setAi.error;
  const busy = setModule.isPending || setAi.isPending;

  const rows = [
    { key: 'support', label: 'Support chat widget', help: 'The chat bubble on every page: FAQs, tickets and hand-off to staff.',
      on: tenant.modules.includes('support'), toggle: (on: boolean) => setModule.mutate({ key: 'support', on }) },
    { key: 'ai', label: 'AI assistant', help: 'Ask AI — answered by the provider set under Integrations below.',
      on: aiOn, toggle: (on: boolean) => setAi.mutate(on), disabled: !catalog.data || integrations.isLoading },
    { key: 'messaging', label: 'In-app messaging', help: 'Chat between customers, professionals and staff.',
      on: tenant.modules.includes('messaging'), toggle: (on: boolean) => setModule.mutate({ key: 'messaging', on }) },
  ];

  return (
    <Card sx={{ mb: 3 }} data-testid="tenant-widgets">
      <CardContent>
        <Typography variant="h6" gutterBottom>Chat & assistant widgets</Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(error, 'The widget could not be changed.')}</Alert>}
        <Stack spacing={0.5}>
          {rows.map((r) => (
            <FormControlLabel
              key={r.key}
              control={<Switch checked={pending[r.key] ?? r.on} disabled={busy || r.disabled} onChange={(e) => r.toggle(e.target.checked)}
                inputProps={{ 'aria-label': r.label }} />}
              label={
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{r.label}</Typography>
                  <Typography variant="caption" color="text.secondary">{r.help}</Typography>
                </Box>
              }
            />
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
};

export default TenantWidgetsCard;
