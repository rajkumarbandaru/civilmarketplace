import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ThemePreview from './ThemePreview';
import { apiErrorMessage } from '../../services/apiError';
import {
  deleteThemePreset,
  fetchThemePresets,
  ResolvedTheme,
  saveThemePreset,
  ThemePreset,
  ThemeUpdateCommand,
} from '../../services/uiConfigApi';

/**
 * The form behind both theme screens — the platform-wide theme and a single workspace's
 * override. They edit the same nine fields with the same "blank means inherit" rule, so they
 * share one component rather than drifting apart.
 */

/** Only the layouts the shell actually implements are offered — see AdminLayout. */
const LAYOUT_STYLES = [
  { value: 'sidebar-left', label: 'Sidebar on the left' },
  { value: 'sidebar-right', label: 'Sidebar on the right' },
  { value: 'topbar', label: 'Top bar — navigation across the top' },
];

/**
 * Quick picks for the colour fields. They are a shortcut for the common case, not a closed set —
 * the text field still takes any CSS colour, so a customer's exact brand hex is never blocked by
 * not being on this row.
 */
const COLOR_SWATCHES = [
  '#667eea', '#764ba2', '#8b5cf6', '#d946ef', '#ec4899', '#f43f5e',
  '#ef4444', '#f97316', '#f59e0b', '#eab308', '#84cc16', '#22c55e',
  '#15803d', '#10b981', '#14b8a6', '#06b6d4', '#0ea5e9', '#0284c7',
  '#2563eb', '#1e3a8a', '#6366f1', '#475569', '#334155', '#1e293b',
  '#0f172a', '#111827', '#57534e', '#78716c', '#f8fafc', '#ffffff',
];

const UI_STYLES = [
  { value: 'default', label: 'Default' },
  { value: 'flat', label: 'Flat — bordered, no shadows' },
  { value: 'elevated', label: 'Elevated — deeper shadows' },
];

const BUTTON_STYLES = [
  { value: 'gradient', label: 'Gradient (primary → accent)' },
  { value: 'solid', label: 'Solid primary' },
  { value: 'outlined', label: 'Outlined' },
];

const MODES = ['light', 'dark', 'system'];
const DENSITIES = ['compact', 'comfortable', 'spacious'];

const toCommand = (form: ThemeUpdateCommand): ThemeUpdateCommand => ({
  ...form,
  // Blank is sent as null, which the backend stores as "inherit" rather than as an empty
  // string — clearing a colour has to be the same state as never having set one.
  primaryColor: form.primaryColor || null,
  accentColor: form.accentColor || null,
  surfaceColor: form.surfaceColor || null,
  sidebarColor: form.sidebarColor || null,
  fontFamily: form.fontFamily || null,
  brandName: form.brandName || null,
  logoUrl: form.logoUrl || null,
  uiStyle: form.uiStyle || null,
  buttonStyle: form.buttonStyle || null,
  layoutStyle: form.layoutStyle || null,
  density: form.density || null,
  borderRadius: form.borderRadius === null || Number.isNaN(form.borderRadius)
    ? null
    : Number(form.borderRadius),
});

const fromResolved = (theme: ResolvedTheme | undefined): ThemeUpdateCommand => ({
  mode: theme?.mode || 'system',
  primaryColor: theme?.primaryColor || '',
  accentColor: theme?.accentColor || '',
  surfaceColor: theme?.surfaceColor || '',
  sidebarColor: theme?.sidebarColor || '',
  borderRadius: theme?.borderRadius ?? null,
  fontFamily: theme?.fontFamily || '',
  brandName: theme?.brandName || '',
  logoUrl: theme?.logoUrl || '',
  uiStyle: theme?.uiStyle || '',
  buttonStyle: theme?.buttonStyle || '',
  layoutStyle: theme?.layoutStyle || '',
  density: theme?.density || '',
});

/**
 * Every styling field at its "inherit" value, matching what {@link fromResolved} produces for an
 * empty row. Applying a preset starts from this so fields the preset does not carry are cleared
 * rather than left behind from the previous selection.
 */
const BLANK_STYLING: ThemeUpdateCommand = {
  mode: 'system',
  primaryColor: '',
  accentColor: '',
  surfaceColor: '',
  sidebarColor: '',
  borderRadius: null,
  fontFamily: '',
  brandName: '',
  logoUrl: '',
  uiStyle: '',
  buttonStyle: '',
  layoutStyle: '',
  density: '',
};

interface Props {
  /** The stored row being edited — nulls in it mean "inherit". */
  value: ResolvedTheme | undefined;
  /** What the scope will actually render, for the fields left blank here. */
  effective?: ResolvedTheme;
  saving: boolean;
  onSave: (command: ThemeUpdateCommand) => void;
  onReset?: () => void;
  resetLabel?: string;
  /** Names the scope in the preview caption — "The platform theme", "Architect's workspace". */
  scopeLabel?: string;
}

const ColorField: React.FC<{
  label: string;
  helper: string;
  value: string | null;
  inherited?: string | null;
  onChange: (value: string) => void;
}> = ({ label, helper, value, inherited, onChange }) => (
  <Stack direction="row" spacing={1.5} alignItems="flex-start">
    <Box
      sx={{
        width: 44,
        height: 44,
        mt: 1,
        borderRadius: 1.5,
        flexShrink: 0,
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: value || inherited || 'transparent',
      }}
    />
    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
      <TextField
        fullWidth
        size="small"
        label={label}
        placeholder={inherited ? `Inherits ${inherited}` : 'Inherits the built-in default'}
        helperText={helper}
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
      />
      <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: 0.75 }}>
        {COLOR_SWATCHES.map((swatch) => (
          <Tooltip key={swatch} title={swatch}>
            <Box
              role="button"
              aria-label={`${label}: ${swatch}`}
              onClick={() => onChange(swatch)}
              sx={{
                width: 18,
                height: 18,
                borderRadius: '50%',
                cursor: 'pointer',
                bgcolor: swatch,
                border: '2px solid',
                // The current value is ringed rather than ticked — a tick would be unreadable on
                // half of these colours.
                borderColor: (value || '').toLowerCase() === swatch ? 'text.primary' : 'divider',
              }}
            />
          </Tooltip>
        ))}
        <Tooltip title="Clear — inherit this colour">
          <Box
            role="button"
            aria-label={`${label}: inherit`}
            onClick={() => onChange('')}
            sx={{
              width: 18,
              height: 18,
              borderRadius: '50%',
              cursor: 'pointer',
              border: '2px dashed',
              borderColor: 'divider',
            }}
          />
        </Tooltip>
      </Stack>
    </Box>
  </Stack>
);

const PRESETS_KEY = ['ui-config', 'theme-presets'];

const ThemeEditor: React.FC<Props> = ({
  value, effective, saving, onSave, onReset, resetLabel, scopeLabel,
}) => {
  const [form, setForm] = useState<ThemeUpdateCommand>(fromResolved(value));

  useEffect(() => setForm(fromResolved(value)), [value]);

  // A failed fetch simply hides the row — presets are a shortcut, not the way to set a theme, so
  // losing them must not block editing one. Custom presets are part of this list, so unlike the
  // shipped-only version it is refetched after a save or delete rather than cached forever.
  const { data: presets } = useQuery<ThemePreset[]>({
    queryKey: PRESETS_KEY,
    queryFn: fetchThemePresets,
  });

  const queryClient = useQueryClient();
  const [saveAsOpen, setSaveAsOpen] = useState(false);
  const [presetName, setPresetName] = useState('');
  const [presetDescription, setPresetDescription] = useState('');
  const [presetError, setPresetError] = useState<string | null>(null);
  // Which preset to show as selected when several hold identical values — the one the admin last
  // applied or saved. Only a tie-breaker: it is ignored the moment it stops matching the form.
  const [preferredPresetKey, setPreferredPresetKey] = useState<string | null>(null);

  const invalidatePresets = () =>
    queryClient.invalidateQueries({ queryKey: PRESETS_KEY });

  const savePreset = useMutation({
    mutationFn: () => saveThemePreset({
      label: presetName.trim(),
      description: presetDescription.trim() || null,
      // The form as it stands, brand name and logo included — the service drops those, so a
      // preset can never carry one customer's wordmark onto another workspace.
      values: form,
    }),
    onSuccess: (saved) => {
      invalidatePresets();
      setSaveAsOpen(false);
      // Saving a preset is a way of naming the look now on screen, so that new preset is the one
      // the picker should show as selected — not whichever older preset happens to hold the same
      // values and sorts earlier.
      setPreferredPresetKey(saved.key);
    },
    onError: (error) => setPresetError(apiErrorMessage(error)),
  });

  const removePreset = useMutation({
    mutationFn: (key: string) => deleteThemePreset(key),
    onSuccess: invalidatePresets,
    onError: (error) => setPresetError(apiErrorMessage(error)),
  });

  const openSaveAs = () => {
    setPresetName('');
    setPresetDescription('');
    setPresetError(null);
    setSaveAsOpen(true);
  };

  const set = <K extends keyof ThemeUpdateCommand>(key: K, v: ThemeUpdateCommand[K]) =>
    setForm((prev) => ({ ...prev, [key]: v }));

  /**
   * Clicking a preset loads exactly its values and nothing else — every styling field the preset
   * leaves unset is blanked rather than kept from whatever was in the form before, so what the
   * chip shows is what the form holds.
   *
   * Clicking the preset that is already selected clears it again: the chip is a toggle, and the
   * way back to "inherit everything" is the same click that applied it.
   *
   * Brand name and logo are the one carve-out in both directions. They identify the customer
   * rather than styling them, no preset carries them, and clearing them here would silently drop
   * the tenant's identity on the way to changing a colour.
   */
  const applyPreset = (preset: ThemePreset) => {
    if (selectedPresetKey === preset.key) {
      setPreferredPresetKey(null);
      return clearStyling();
    }
    setPreferredPresetKey(preset.key);
    return setFormFromPreset(preset);
  };

  const setFormFromPreset = (preset: ThemePreset) =>
    setForm((prev) => ({
      ...BLANK_STYLING,
      ...preset.values,
      brandName: prev.brandName,
      logoUrl: prev.logoUrl,
    }));

  const clearStyling = () =>
    setForm((prev) => ({
      ...BLANK_STYLING,
      brandName: prev.brandName,
      logoUrl: prev.logoUrl,
    }));

  /**
   * Which preset the form is currently sitting on, derived from the values rather than remembered
   * from the last click — so editing any field afterwards drops the highlight by itself, and the
   * chip never claims a preset is loaded once the form no longer matches it.
   *
   * The comparison covers exactly the fields {@link applyPreset} writes, and no others — only
   * brand name and logo are excluded, since no preset carries them. Applying a preset now writes
   * its values verbatim, so every field it does carry, including layoutStyle, must match for the
   * chip to claim it is loaded.
   */
  const selectedPresetKey = useMemo(() => {
    const matches = (presets || []).filter((preset) =>
      (Object.keys(preset.values) as (keyof ThemeUpdateCommand)[])
        .filter((key) => key !== 'brandName' && key !== 'logoUrl')
        .every((key) => (preset.values[key] ?? null) === (form[key] ?? null))
    );
    // Saving the current form as a preset necessarily creates a second preset holding the same
    // values, so ties are normal rather than exceptional. The one the admin last touched wins;
    // otherwise the first match does.
    const preferred = matches.find((preset) => preset.key === preferredPresetKey);
    return (preferred ?? matches[0])?.key ?? null;
  }, [presets, form, preferredPresetKey]);

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Theme &amp; UI style
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Leave a field blank to inherit it. A blank colour is not black — it means the layer
          underneath decides, so a change there still reaches this scope later.
        </Typography>

        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>Start from a preset</Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {(presets || []).map((preset) => (
              <Tooltip key={preset.key} title={preset.description}>
                <Chip
                  label={preset.label}
                  // The applied preset is filled and outlined in the accent colour. Outlined-only
                  // chips all render the same, so before this there was no way to see which one
                  // the form was showing — the click looked like it had done nothing.
                  variant={selectedPresetKey === preset.key ? 'filled' : 'outlined'}
                  color={selectedPresetKey === preset.key ? 'primary' : 'default'}
                  sx={selectedPresetKey === preset.key
                    ? { fontWeight: 600, borderColor: 'primary.main', border: 1 }
                    : undefined}
                  onClick={() => applyPreset(preset)}
                  // Only a saved preset gets a delete affordance; the shipped ones are part of
                  // the build and deleting one would come back on the next deploy anyway.
                  onDelete={preset.builtIn ? undefined : () => removePreset.mutate(preset.key)}
                  avatar={
                    <Box
                      sx={{
                        width: 18,
                        height: 18,
                        borderRadius: '50%',
                        background: `linear-gradient(135deg, ${preset.values.primaryColor}, ${preset.values.accentColor})`,
                      }}
                    />
                  }
                />
              </Tooltip>
            ))}
            <Chip
              icon={<AddIcon />}
              label="Save current as preset"
              variant="outlined"
              color="primary"
              onClick={openSaveAs}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Fills the fields below with that preset's values and clears the rest — click the
            selected preset again to empty them. Nothing is saved until you press save, and your
            brand name and logo are left as they are.
          </Typography>
          {presetError && (
            <Alert severity="error" sx={{ mt: 1 }} onClose={() => setPresetError(null)}>
              {presetError}
            </Alert>
          )}
        </Box>

        <Dialog open={saveAsOpen} onClose={() => setSaveAsOpen(false)} fullWidth maxWidth="sm">
          <DialogTitle>Save as a preset</DialogTitle>
          <DialogContent>
            <DialogContentText sx={{ mb: 2 }}>
              Stores the style values currently in the form so any workspace can start from them.
              This does not change what this scope is painting — the theme itself is still saved
              with the button below. Brand name and logo are not part of a preset.
            </DialogContentText>
            <Stack spacing={2}>
              <TextField
                autoFocus
                label="Name"
                fullWidth
                value={presetName}
                onChange={(e) => setPresetName(e.target.value)}
                inputProps={{ maxLength: 60 }}
                helperText="Saving under an existing name replaces that preset."
              />
              <TextField
                label="Description (optional)"
                fullWidth
                multiline
                minRows={2}
                value={presetDescription}
                onChange={(e) => setPresetDescription(e.target.value)}
                inputProps={{ maxLength: 200 }}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setSaveAsOpen(false)}>Cancel</Button>
            <Button
              variant="contained"
              disabled={!presetName.trim() || savePreset.isPending}
              onClick={() => { setPresetError(null); savePreset.mutate(); }}
            >
              {savePreset.isPending ? 'Saving…' : 'Save preset'}
            </Button>
          </DialogActions>
        </Dialog>

        <Grid container spacing={2.5}>
          <Grid item xs={12} sm={6}>
            <TextField
              select fullWidth size="small" label="Colour mode" value={form.mode}
              helperText="'system' follows each viewer's OS setting"
              onChange={(e) => set('mode', e.target.value)}
            >
              {MODES.map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select fullWidth size="small" label="Density" value={form.density || ''}
              helperText="How much fits on a screen"
              onChange={(e) => set('density', e.target.value)}
            >
              <MenuItem value="">Inherit</MenuItem>
              {DENSITIES.map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <ColorField
              label="Primary colour"
              helper="Buttons, links, the active menu row"
              value={form.primaryColor}
              inherited={effective?.primaryColor}
              onChange={(v) => set('primaryColor', v)}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <ColorField
              label="Accent colour"
              helper="The second stop of gradients and highlights"
              value={form.accentColor}
              inherited={effective?.accentColor}
              onChange={(v) => set('accentColor', v)}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <ColorField
              label="Surface colour"
              helper="Cards and the top bar"
              value={form.surfaceColor}
              inherited={effective?.surfaceColor}
              onChange={(v) => set('surfaceColor', v)}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <ColorField
              label="Sidebar colour"
              helper="The navigation bar. Labels flip to dark text on a pale colour."
              value={form.sidebarColor}
              inherited={effective?.sidebarColor}
              onChange={(v) => set('sidebarColor', v)}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth size="small" type="number" label="Corner radius (px)"
              helperText="Blank inherits the shipped 12px"
              value={form.borderRadius ?? ''}
              onChange={(e) => set('borderRadius', e.target.value === '' ? null : Number(e.target.value))}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select fullWidth size="small" label="Navigation position" value={form.layoutStyle || ''}
              helperText="Members cannot change this — it stays consistent per workspace"
              onChange={(e) => set('layoutStyle', e.target.value)}
            >
              <MenuItem value="">Inherit</MenuItem>
              {LAYOUT_STYLES.map((l) => <MenuItem key={l.value} value={l.value}>{l.label}</MenuItem>)}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select fullWidth size="small" label="UI style" value={form.uiStyle || ''}
              onChange={(e) => set('uiStyle', e.target.value)}
            >
              <MenuItem value="">Inherit</MenuItem>
              {UI_STYLES.map((s) => <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>)}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select fullWidth size="small" label="Button style" value={form.buttonStyle || ''}
              helperText="How filled buttons are painted"
              onChange={(e) => set('buttonStyle', e.target.value)}
            >
              <MenuItem value="">Inherit</MenuItem>
              {BUTTON_STYLES.map((b) => <MenuItem key={b.value} value={b.value}>{b.label}</MenuItem>)}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth size="small" label="Brand name"
              placeholder="Inherits CivEngMarket"
              helperText="The wordmark in the sidebar"
              value={form.brandName || ''}
              onChange={(e) => set('brandName', e.target.value)}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth size="small" label="Logo URL"
              placeholder="https://… or /logo.svg"
              helperText="Shown beside the wordmark. Blank shows no image."
              value={form.logoUrl || ''}
              onChange={(e) => set('logoUrl', e.target.value)}
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              fullWidth size="small" label="Font family"
              placeholder="Inherits 'Inter', 'Poppins', 'Roboto', sans-serif"
              helperText="A CSS font stack. The fonts have to be available to the browser."
              value={form.fontFamily || ''}
              onChange={(e) => set('fontFamily', e.target.value)}
            />
          </Grid>
        </Grid>

        <Stack direction="row" spacing={1.5} sx={{ mt: 3 }}>
          <Button variant="contained" disabled={saving} onClick={() => onSave(toCommand(form))}>
            {saving ? 'Saving…' : 'Save theme'}
          </Button>
          {onReset && (
            <Button color="inherit" disabled={saving} onClick={onReset}>
              {resetLabel || 'Reset'}
            </Button>
          )}
        </Stack>

        <Box sx={{ mt: 3 }}>
          <ThemePreview
            form={form}
            inherited={effective}
            scopeLabel={scopeLabel || 'This scope'}
          />
        </Box>
      </CardContent>
    </Card>
  );
};

export default ThemeEditor;
