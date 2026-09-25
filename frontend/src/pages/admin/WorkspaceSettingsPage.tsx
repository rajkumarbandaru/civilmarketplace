import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Stack, Switch, Typography,
} from '@mui/material';
import { apiErrorMessage } from '../../services/apiError';
import { moduleLabel } from '../../services/tenantApi';
import { workspaceIntegrations } from '../../services/tenantIntegrationApi';
import {
  WorkspaceModule, WorkspaceModules, fetchWorkspaceModules, saveWorkspaceModules, toggledModules,
} from '../../services/workspaceSettingsApi';
import { IntegrationsPanel } from './TenantIntegrationsCard';

/** One module's row: on/off within the plan, with why it cannot be changed when it cannot. */
const ModuleRow: React.FC<{
  module: WorkspaceModule;
  busy: boolean;
  onToggle: (on: boolean) => void;
}> = ({ module: m, busy, onToggle }) => {
  const note = m.locked ? 'Always on'
    : !m.entitled && m.chosen ? 'Not in your plan — kept, but not running'
    : !m.entitled ? 'Not in your plan'
    : null;
  return (
    <Stack direction="row" alignItems="center" spacing={1} data-testid={`module-${m.key}`}
      sx={{ py: 0.75, borderBottom: 1, borderColor: 'divider' }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography fontWeight={600}>{moduleLabel(m.key)}</Typography>
        {note && <Typography variant="caption" color="text.secondary">{note}</Typography>}
      </Box>
      {m.running && <Chip size="small" color="success" label="Running" />}
      <Switch
        checked={m.chosen}
        disabled={busy || m.locked || (!m.entitled && !m.chosen)}
        onChange={(e) => onToggle(e.target.checked)}
        inputProps={{ 'aria-label': `${moduleLabel(m.key)} on or off` }}
      />
    </Stack>
  );
};

/**
 * A workspace's own settings, run by its own admins: which modules (and so which menus and
 * pages) it runs, which provider accounts its payments, messages and AI assistant use, and a way
 * to its team. Everything starts on the platform's defaults.
 */
const WorkspaceSettingsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const modules = useQuery({ queryKey: ['workspace-modules'], queryFn: fetchWorkspaceModules, retry: false });
  const save = useMutation({
    mutationFn: (next: string[]) => saveWorkspaceModules(next),
    // The switch moves at once; a refused save puts it back.
    onMutate: (next: string[]) => {
      const before = queryClient.getQueryData<WorkspaceModules>(['workspace-modules']);
      if (before) {
        queryClient.setQueryData<WorkspaceModules>(['workspace-modules'], {
          ...before,
          modules: before.modules.map((m) => (m.locked ? m : { ...m, chosen: next.includes(m.key) })),
        });
      }
      return { before };
    },
    onError: (_error, _next, context) => {
      if (context?.before) queryClient.setQueryData(['workspace-modules'], context.before);
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['workspace-modules'], data);
      // The menu is built from the running modules.
      queryClient.invalidateQueries({ queryKey: ['ui-config'] });
    },
  });

  if (modules.isLoading) return <CircularProgress />;
  if (modules.isError || !modules.data) {
    return (
      <Alert severity="info" data-testid="workspace-settings-unavailable">
        {apiErrorMessage(modules.error, 'Workspace settings could not be loaded.')}
      </Alert>
    );
  }
  const optional = modules.data.modules.filter((m) => !m.locked);
  const locked = modules.data.modules.filter((m) => m.locked);

  return (
    <Box data-testid="workspace-settings">
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="h6">Modules</Typography>
            <Chip size="small" color="primary" label={`${modules.data.planName} plan`} />
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Switch features on or off for your workspace. A module that is off disappears from every
            menu and its pages close; its data is kept, so switching it back on brings everything back.
          </Typography>
          {save.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(save.error, 'The change was not saved.')}</Alert>
          )}
          {optional.map((m) => (
            <ModuleRow key={m.key} module={m} busy={save.isPending}
              onToggle={(on) => save.mutate(toggledModules(modules.data!.modules, m.key, on))} />
          ))}
          <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1.5 }}>
            Always on: {locked.map((m) => moduleLabel(m.key)).join(', ')}.
          </Typography>
        </CardContent>
      </Card>

      <IntegrationsPanel
        name="your workspace"
        client={workspaceIntegrations}
        intro="Payments, email, SMS, WhatsApp and the AI assistant run on the platform's accounts by default. Connect your own account for any of them — for example your own Razorpay merchant account, or OpenAI or Anthropic instead of Gemini for the assistant — or switch one off."
      />

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Team</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Add people to this workspace with a role. They get an email to set their own password.
          </Typography>
          <Button variant="outlined" component={RouterLink} to="/admin/users">Manage users</Button>
        </CardContent>
      </Card>
    </Box>
  );
};

export default WorkspaceSettingsPage;
