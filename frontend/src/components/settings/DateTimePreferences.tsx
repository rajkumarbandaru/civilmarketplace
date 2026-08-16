import React from 'react';
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
import { useUiConfig } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  AppearanceSettings,
  fetchMyAppearance,
  updateMyAppearance,
} from '../../services/uiConfigApi';
import {
  availableTimezones,
  browserTimezone,
  describeTimezone,
  DEFAULT_DATE_FORMAT,
  formatDate,
} from '../../utils/datetime';

/** Shared with AppearancePage so both screens read and write the one cached copy. */
export const APPEARANCE_QUERY_KEY = ['ui-config', 'my-appearance'];

/**
 * Timezone and date format — the two settings that decide how every date on the site reads.
 *
 * Lifted out of the Appearance screen so it can live under Settings, where people look for it:
 * "what timezone am I in" is not a question about how the workspace *looks*, and it was buried
 * below colour mode and density. Extracted as a component rather than duplicated, so the two
 * screens cannot drift into disagreeing about what the current value is.
 *
 * These are per-person, not per-platform. The surrounding screen says so; this component only
 * renders the controls.
 */
const DateTimePreferences: React.FC = () => {
  const queryClient = useQueryClient();
  const { refresh } = useUiConfig();

  const { data, isLoading, isError } = useQuery<AppearanceSettings>({
    queryKey: APPEARANCE_QUERY_KEY,
    queryFn: fetchMyAppearance,
  });

  const save = useMutation({
    mutationFn: (command: {
      colorMode: string | null;
      density: string | null;
      timezone: string | null;
      dateFormat: string | null;
    }) => updateMyAppearance(command),
    onSuccess: (saved: AppearanceSettings) => {
      queryClient.setQueryData(APPEARANCE_QUERY_KEY, saved);
      refresh();
    },
  });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}><CircularProgress size={24} /></Box>;
  }
  if (isError || !data) {
    return <Alert severity="error">Could not load your date and time settings.</Alert>;
  }

  const busy = save.isPending;

  // The colour-mode and density values are passed through untouched: the endpoint takes the whole
  // appearance record, so sending only the two fields edited here would blank the other two.
  const change = (field: 'timezone' | 'dateFormat', value: string) =>
    save.mutate({
      colorMode: data.myColorMode,
      density: data.myDensity,
      timezone: field === 'timezone' ? value || null : data.myTimezone,
      dateFormat: field === 'dateFormat' ? value || null : data.myDateFormat,
    });

  // Previewed against a fixed date rather than "now", so the difference between the layouts is
  // visible at a glance — on the 3rd of a month, DD/MM and MM/DD look identical.
  const sampleDate = new Date('2026-03-09T15:45:00Z');
  const effectiveZone = data.myTimezone || browserTimezone();

  return (
    <Stack spacing={3}>
      {save.isError && (
        <Alert severity="error">
          {apiErrorMessage(save.error, 'That setting could not be saved.')}
        </Alert>
      )}

      <Box>
        <Typography variant="subtitle2">Date and time</Typography>
        <Typography variant="body2" color="text.secondary">
          Applies to every date and time you see, on every page and in every workspace.
        </Typography>
      </Box>

      <TextField
        select fullWidth size="small" label="Timezone" disabled={busy}
        value={data.myTimezone || ''}
        helperText={
          data.myTimezone
            ? `Times are shown in ${describeTimezone(data.myTimezone)}`
            : `Following your device, which is ${describeTimezone(browserTimezone())}`
        }
        onChange={(e) => change('timezone', e.target.value)}
        // A native select: the list runs to several hundred zones, and MUI's rendered menu
        // mounts every one of them as a node.
        SelectProps={{ native: true }}
        InputLabelProps={{ shrink: true }}
      >
        <option value="">Use my device&apos;s timezone</option>
        {availableTimezones().map((zone) => (
          <option key={zone} value={zone}>{zone.replace(/_/g, ' ')}</option>
        ))}
      </TextField>

      <TextField
        select fullWidth size="small" label="Date format" disabled={busy}
        value={data.myDateFormat || ''}
        helperText={`Dates appear as ${formatDate(sampleDate, {
          timezone: effectiveZone,
          dateFormat: data.myDateFormat,
        })}`}
        onChange={(e) => change('dateFormat', e.target.value)}
      >
        <MenuItem value="">
          Site default ({formatDate(sampleDate, {
            timezone: effectiveZone,
            dateFormat: DEFAULT_DATE_FORMAT,
          })})
        </MenuItem>
        {data.dateFormatOptions.map((option) => (
          <MenuItem key={option} value={option}>
            {option} — {formatDate(sampleDate, {
              timezone: effectiveZone,
              dateFormat: option,
            })}
          </MenuItem>
        ))}
      </TextField>
    </Stack>
  );
};

export default DateTimePreferences;
