import React, { useEffect, useState } from 'react';
import {
  Box, Card, Typography, Grid, TextField, MenuItem, Switch, FormControlLabel, Button,
  Skeleton, Snackbar, Alert, Stack, Chip, Divider, InputAdornment, Tooltip, IconButton,
} from '@mui/material';
import { Save, RestartAlt, Undo } from '@mui/icons-material';
import {
  settingsApi, PlatformSetting, PlatformSettingsData, SettingsGroup,
} from '../../services/adminApi';
import { apiErrorMessage } from '../../services/apiError';

/**
 * Platform-wide settings — the switches that change how the platform behaves for everybody.
 *
 * The form is built from the catalogue the backend serves, not from a list written here: the type,
 * bounds and choices that validate a value on save are the same ones that render its editor, so a
 * field cannot offer something the server will reject.
 */
const PlatformSettingsPage: React.FC = () => {
  const [groups, setGroups] = useState<SettingsGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  /** Edits not yet saved, keyed by setting. Empty means the form matches the server. */
  const [pending, setPending] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null);

  const applyData = (data: PlatformSettingsData) => {
    setGroups(data?.groups ?? []);
    setPending({});
  };

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const response = await settingsApi.getSettings();
        applyData(response.data.data);
      } catch (err) {
        setToast({ message: apiErrorMessage(err, 'Settings could not be loaded'), severity: 'error' });
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const valueOf = (setting: PlatformSetting) =>
    pending[setting.key] !== undefined ? pending[setting.key] : setting.value;

  const change = (setting: PlatformSetting, value: string) => {
    setPending((current) => {
      const next = { ...current };
      // Typing a value back to what the server holds is not a change — dropping it here is what
      // keeps the "unsaved" count and the Save button honest.
      if (value === setting.value) delete next[setting.key];
      else next[setting.key] = value;
      return next;
    });
  };

  const dirtyKeys = Object.keys(pending);

  const handleSave = async () => {
    if (dirtyKeys.length === 0) return;
    try {
      setSaving(true);
      const response = await settingsApi.updateSettings(pending);
      applyData(response.data.data);
      setToast({ message: 'Settings saved', severity: 'success' });
    } catch (err) {
      // The backend rejects the whole submission if any field is invalid, so nothing local is
      // cleared here — the admin keeps their edits and can correct the one that failed.
      setToast({ message: apiErrorMessage(err, 'Settings could not be saved'), severity: 'error' });
    } finally {
      setSaving(false);
    }
  };

  const handleResetSetting = async (setting: PlatformSetting) => {
    try {
      const response = await settingsApi.resetSetting(setting.key);
      applyData(response.data.data);
      setToast({ message: `${setting.label} reset to its default`, severity: 'success' });
    } catch (err) {
      setToast({ message: apiErrorMessage(err, 'The setting could not be reset'), severity: 'error' });
    }
  };

  const renderEditor = (setting: PlatformSetting) => {
    const value = valueOf(setting);
    const dirty = pending[setting.key] !== undefined;

    if (setting.type === 'BOOLEAN') {
      return (
        <FormControlLabel
          control={
            <Switch
              checked={value === 'true'}
              onChange={(e) => change(setting, e.target.checked ? 'true' : 'false')}
            />
          }
          label={
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {setting.label}
                {dirty && <Chip size="small" label="unsaved" sx={{ ml: 1, height: 18 }} />}
              </Typography>
              <Typography variant="caption" color="text.secondary">{setting.help}</Typography>
            </Box>
          }
          sx={{ alignItems: 'flex-start', m: 0 }}
        />
      );
    }

    const isNumeric = setting.type === 'NUMBER' || setting.type === 'PERCENT';

    return (
      <TextField
        fullWidth
        size="small"
        select={setting.type === 'CHOICE'}
        type={isNumeric ? 'number' : setting.type === 'EMAIL' ? 'email' : 'text'}
        label={setting.label}
        value={value}
        onChange={(e) => change(setting, e.target.value)}
        helperText={setting.help}
        inputProps={isNumeric ? { min: setting.min ?? undefined, max: setting.max ?? undefined } : undefined}
        InputProps={setting.type === 'PERCENT'
          ? { endAdornment: <InputAdornment position="end">%</InputAdornment> }
          : undefined}
      >
        {setting.choices.map((choice) => (
          <MenuItem key={choice} value={choice}>{choice}</MenuItem>
        ))}
      </TextField>
    );
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2, mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Settings</Typography>
          <Typography variant="body2" color="text.secondary">
            Platform-wide behaviour. The console's colours and menus live under Theme &amp; UI style.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          {dirtyKeys.length > 0 && (
            <>
              <Chip size="small" color="warning" label={`${dirtyKeys.length} unsaved`} />
              <Button startIcon={<Undo />} onClick={() => setPending({})} sx={{ borderRadius: 2 }}>
                Discard
              </Button>
            </>
          )}
          <Button
            startIcon={<Save />}
            variant="contained"
            disabled={dirtyKeys.length === 0 || saving}
            onClick={handleSave}
            sx={{ borderRadius: 2 }}
          >
            {saving ? 'Saving…' : 'Save changes'}
          </Button>
        </Stack>
      </Box>

      {loading ? (
        <Grid container spacing={3}>
          {Array.from({ length: 4 }).map((_, idx) => (
            <Grid item xs={12} md={6} key={idx}>
              <Card sx={{ borderRadius: 3, p: 3 }}><Skeleton height={220} /></Card>
            </Grid>
          ))}
        </Grid>
      ) : groups.length === 0 ? (
        <Card sx={{ borderRadius: 3, p: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">No settings are available.</Typography>
        </Card>
      ) : (
        <Grid container spacing={3}>
          {groups.map((group) => (
            <Grid item xs={12} md={6} key={group.group}>
              <Card sx={{ borderRadius: 3, height: '100%' }}>
                <Box sx={{ p: 2.5, borderBottom: 1, borderColor: 'divider' }}>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>{group.group}</Typography>
                </Box>
                <Box sx={{ p: 2.5 }}>
                  <Stack spacing={2.5} divider={<Divider flexItem />}>
                    {group.settings.map((setting) => (
                      <Box key={setting.key} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                        <Box sx={{ flexGrow: 1, minWidth: 0 }}>{renderEditor(setting)}</Box>
                        {/* Only an overridden setting can be reset — the button would be a no-op
                            on one that is already following the shipped default. */}
                        {setting.customised && (
                          <Tooltip title={`Reset to default (${setting.defaultValue || 'empty'})`}>
                            <IconButton size="small" onClick={() => handleResetSetting(setting)}
                              aria-label={`Reset ${setting.label}`}>
                              <RestartAlt fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Box>
                    ))}
                  </Stack>
                </Box>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      <Snackbar
        open={toast !== null}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity={toast?.severity ?? 'success'} onClose={() => setToast(null)} variant="filled">
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default PlatformSettingsPage;
