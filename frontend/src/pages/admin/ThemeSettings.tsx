import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  CircularProgress,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ThemeEditor from '../../components/admin/ThemeEditor';
import { apiErrorMessage } from '../../services/apiError';
import { useUiConfig } from '../../providers/UiConfigProvider';
import {
  fetchEffectiveWorkspaceTheme,
  fetchPlatformTheme,
  fetchWorkspaces,
  fetchWorkspaceTheme,
  resetWorkspaceTheme,
  ThemeUpdateCommand,
  updatePlatformTheme,
  updateWorkspaceTheme,
  WorkspaceSummary,
} from '../../services/uiConfigApi';

const PLATFORM = '__platform__';

/**
 * The theme screen for both scopes. It opens on the platform-wide theme — the base every
 * workspace inherits — and the picker at the top switches the same editor onto one workspace's
 * override, so an admin comparing the two does not have to move between screens.
 *
 * The platform row has no reset: it is the bottom of the inheritance chain, so deleting it would
 * leave nothing to inherit from. A workspace override does, and resetting it returns the
 * workspace to the platform theme.
 */
const ThemeSettings: React.FC = () => {
  const queryClient = useQueryClient();
  const { refresh } = useUiConfig();
  const [scope, setScope] = useState<string>(PLATFORM);
  const isPlatform = scope === PLATFORM;

  const { data: workspaces } = useQuery<WorkspaceSummary[]>({
    queryKey: ['ui-config', 'workspaces'],
    queryFn: fetchWorkspaces,
  });

  const themeKey = isPlatform
    ? ['ui-config', 'platform-theme']
    : ['ui-config', 'workspace-theme', scope];

  const { data, isLoading, isError } = useQuery({
    queryKey: themeKey,
    queryFn: () => (isPlatform ? fetchPlatformTheme() : fetchWorkspaceTheme(scope)),
  });

  // What the scope actually paints. For the platform that is its own row; for a workspace it is
  // the merge over the platform, which is what the editor shows behind the blank fields.
  const { data: effective } = useQuery({
    queryKey: [...themeKey, 'effective'],
    queryFn: () => (isPlatform ? fetchPlatformTheme() : fetchEffectiveWorkspaceTheme(scope)),
  });

  const onSaved = (saved: typeof data) => {
    queryClient.setQueryData(themeKey, saved);
    queryClient.invalidateQueries({ queryKey: [...themeKey, 'effective'] });
    queryClient.invalidateQueries({ queryKey: ['ui-config', 'workspaces'] });
    // Re-read the signed-in user's snapshot so the change is visible immediately rather than
    // after a reload — this admin is looking at the theme they just edited.
    refresh();
  };

  const save = useMutation({
    mutationFn: (command: ThemeUpdateCommand) =>
      (isPlatform ? updatePlatformTheme(command) : updateWorkspaceTheme(scope, command)),
    onSuccess: onSaved,
  });

  const reset = useMutation({
    mutationFn: () => resetWorkspaceTheme(scope),
    onSuccess: onSaved,
  });

  const workspaceLabel = workspaces?.find((w) => w.role === scope)?.label || scope;
  const scopeLabel = isPlatform ? 'The platform theme' : `${workspaceLabel}'s workspace`;

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Theme &amp; UI style</Typography>

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        alignItems={{ sm: 'center' }}
        sx={{ mb: 3 }}
      >
        <TextField
          select
          size="small"
          label="Workspace"
          value={scope}
          sx={{ minWidth: 260 }}
          onChange={(e) => { setScope(e.target.value); save.reset(); reset.reset(); }}
        >
          <MenuItem value={PLATFORM}>Platform — every workspace</MenuItem>
          {(workspaces || []).map((workspace) => (
            <MenuItem key={workspace.role} value={workspace.role}>
              {workspace.label}
              {workspace.themeCustomised ? ' — customised' : ''}
            </MenuItem>
          ))}
        </TextField>
        <Typography variant="body2" color="text.secondary">
          {isPlatform
            ? `Applies to every workspace that has not overridden it. Version ${data?.version ?? '—'}.`
            : `Overrides the platform theme for ${workspaceLabel} only. Fields left blank inherit it.`}
        </Typography>
      </Stack>

      {save.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {apiErrorMessage(save.error, 'The theme could not be saved.')}
        </Alert>
      )}
      {save.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>Theme saved.</Alert>}
      {reset.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {apiErrorMessage(reset.error, 'The override could not be removed.')}
        </Alert>
      )}
      {reset.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          This workspace follows the platform theme again.
        </Alert>
      )}

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>
      )}
      {isError && <Alert severity="error">Could not load this theme.</Alert>}

      {!isLoading && !isError && (
        <ThemeEditor
          // Remounting on a scope change drops the previous scope's unsaved edits rather than
          // carrying them into the next one, where saving would apply them to the wrong theme.
          key={scope}
          value={data}
          effective={effective || data}
          saving={save.isPending || reset.isPending}
          scopeLabel={scopeLabel}
          onSave={(command) => save.mutate(command)}
          onReset={isPlatform ? undefined : () => reset.mutate()}
          resetLabel="Follow the platform theme"
        />
      )}
    </Box>
  );
};

export default ThemeSettings;
