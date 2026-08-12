import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, CircularProgress, Typography } from '@mui/material';
import ThemeEditor from '../../components/admin/ThemeEditor';
import { useUiConfig } from '../../providers/UiConfigProvider';
import {
  fetchPlatformTheme,
  ThemeUpdateCommand,
  updatePlatformTheme,
} from '../../services/uiConfigApi';

const QUERY_KEY = ['ui-config', 'platform-theme'];

/**
 * The platform-wide theme — the base every workspace inherits. There is no reset here: the
 * platform row is the bottom of the inheritance chain, so deleting it would leave nothing to
 * inherit from. Per-workspace overrides live on the Workspaces screen.
 */
const ThemeSettings: React.FC = () => {
  const queryClient = useQueryClient();
  const { refresh } = useUiConfig();

  const { data, isLoading, isError } = useQuery({
    queryKey: QUERY_KEY,
    queryFn: fetchPlatformTheme,
  });

  const save = useMutation({
    mutationFn: (command: ThemeUpdateCommand) => updatePlatformTheme(command),
    onSuccess: (saved) => {
      queryClient.setQueryData(QUERY_KEY, saved);
      // Re-read the signed-in user's snapshot so the change is visible immediately rather than
      // after a reload — this admin is looking at the theme they just edited.
      refresh();
    },
  });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }

  if (isError) {
    return <Alert severity="error">Could not load the platform theme.</Alert>;
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Theme &amp; UI style</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Applies to every workspace that has not overridden it. Version {data?.version}.
      </Typography>

      {save.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(save.error as any)?.response?.data?.message || 'The theme could not be saved.'}
        </Alert>
      )}
      {save.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>Theme saved.</Alert>}

      <ThemeEditor
        value={data}
        effective={data}
        saving={save.isPending}
        scopeLabel="The platform theme"
        onSave={(command) => save.mutate(command)}
      />
    </Box>
  );
};

export default ThemeSettings;
