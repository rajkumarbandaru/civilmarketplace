import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Container,
  Divider,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useUiConfig } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  AppearanceSettings,
  fetchMyAppearance,
  resetMyAppearance,
  updateMyAppearance,
} from '../../services/uiConfigApi';

const QUERY_KEY = ['ui-config', 'my-appearance'];

/** Human copy for the fields Super Admin owns, keyed by the names the API returns. */
const ADMIN_FIELD_LABELS: Record<string, string> = {
  layoutStyle: 'Navigation position',
  uiStyle: 'UI style',
  buttonStyle: 'Button style',
  primaryColor: 'Primary colour',
  accentColor: 'Accent colour',
  surfaceColor: 'Surface colour',
  sidebarColor: 'Sidebar colour',
  borderRadius: 'Corner radius',
  fontFamily: 'Font',
  brandName: 'Brand name',
  logoUrl: 'Logo',
};

/**
 * A member's own appearance settings: light/dark and density, and nothing else.
 *
 * The split between what a member may change and what Super Admin owns comes from the API's
 * `memberEditable` / `adminControlled` lists rather than being hardcoded here — if that line ever
 * moves, this screen follows it without an edit.
 */
const AppearancePage: React.FC = () => {
  const queryClient = useQueryClient();
  const { refresh } = useUiConfig();

  const { data, isLoading, isError } = useQuery<AppearanceSettings>({
    queryKey: QUERY_KEY,
    queryFn: fetchMyAppearance,
  });

  const applySaved = (saved: AppearanceSettings) => {
    queryClient.setQueryData(QUERY_KEY, saved);
    refresh();
  };

  const save = useMutation({
    mutationFn: (command: { colorMode: string | null; density: string | null }) =>
      updateMyAppearance(command),
    onSuccess: applySaved,
  });

  const reset = useMutation({ mutationFn: resetMyAppearance, onSuccess: applySaved });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}><CircularProgress /></Box>;
  }
  if (isError || !data) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">Could not load your appearance settings.</Alert>
      </Container>
    );
  }

  const busy = save.isPending || reset.isPending;

  // An empty string is sent as null, which means "follow the workspace" — deliberately not the
  // same as picking the value the workspace happens to use today.
  const change = (field: 'colorMode' | 'density', value: string) =>
    save.mutate({
      colorMode: field === 'colorMode' ? value || null : data.myColorMode,
      density: field === 'density' ? value || null : data.myDensity,
    });

  return (
    <Container maxWidth="sm" sx={{ py: 5 }}>
      <Typography variant="h4" gutterBottom>Appearance</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        How the {data.workspaceLabel} workspace looks on your screen.
      </Typography>

      {save.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {apiErrorMessage(save.error, 'That setting could not be saved.')}
        </Alert>
      )}

      <Card>
        <CardContent>
          <Stack spacing={3}>
            <TextField
              select fullWidth size="small" label="Colour mode" disabled={busy}
              value={data.myColorMode || ''}
              helperText={`Following the workspace uses "${data.effective.mode}"`}
              onChange={(e) => change('colorMode', e.target.value)}
            >
              <MenuItem value="">Follow the workspace</MenuItem>
              {data.colorModeOptions.map((option) => (
                <MenuItem key={option} value={option}>{option}</MenuItem>
              ))}
            </TextField>

            <TextField
              select fullWidth size="small" label="Density" disabled={busy}
              value={data.myDensity || ''}
              helperText={`Following the workspace uses "${data.effective.density || 'comfortable'}"`}
              onChange={(e) => change('density', e.target.value)}
            >
              <MenuItem value="">Follow the workspace</MenuItem>
              {data.densityOptions.map((option) => (
                <MenuItem key={option} value={option}>{option}</MenuItem>
              ))}
            </TextField>

            <Button
              color="inherit"
              disabled={busy || (!data.myColorMode && !data.myDensity)}
              onClick={() => reset.mutate()}
            >
              Follow the workspace on both
            </Button>
          </Stack>

          <Divider sx={{ my: 3 }} />

          <Typography variant="subtitle2" gutterBottom>Set by your administrator</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            These stay the same for everyone in this workspace.
          </Typography>

          <Stack spacing={1}>
            {data.adminControlled.map((field) => {
              const value = (data.effective as unknown as Record<string, unknown>)[field];
              return (
                <Stack key={field} direction="row" justifyContent="space-between">
                  <Typography variant="body2" color="text.secondary">
                    {ADMIN_FIELD_LABELS[field] || field}
                  </Typography>
                  <Typography variant="body2">
                    {value === null || value === undefined || value === ''
                      ? 'Default'
                      : String(value)}
                  </Typography>
                </Stack>
              );
            })}
          </Stack>
        </CardContent>
      </Card>
    </Container>
  );
};

export default AppearancePage;
