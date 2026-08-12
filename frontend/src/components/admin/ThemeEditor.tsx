import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ThemePreview from './ThemePreview';
import {
  fetchThemePresets,
  ResolvedTheme,
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
    <TextField
      fullWidth
      size="small"
      label={label}
      placeholder={inherited ? `Inherits ${inherited}` : 'Inherits the built-in default'}
      helperText={helper}
      value={value || ''}
      onChange={(e) => onChange(e.target.value)}
    />
  </Stack>
);

const ThemeEditor: React.FC<Props> = ({
  value, effective, saving, onSave, onReset, resetLabel, scopeLabel,
}) => {
  const [form, setForm] = useState<ThemeUpdateCommand>(fromResolved(value));

  useEffect(() => setForm(fromResolved(value)), [value]);

  // Presets are shipped with the service and change only on deploy, so they are fetched once and
  // never refetched. A failed fetch simply hides the row — presets are a shortcut, not the way
  // to set a theme, so losing them must not block editing one.
  const { data: presets } = useQuery<ThemePreset[]>({
    queryKey: ['ui-config', 'theme-presets'],
    queryFn: fetchThemePresets,
    staleTime: Infinity,
  });

  const set = <K extends keyof ThemeUpdateCommand>(key: K, v: ThemeUpdateCommand[K]) =>
    setForm((prev) => ({ ...prev, [key]: v }));

  /**
   * Loads a preset into the form without saving, and without touching brand name or logo — those
   * identify the customer rather than styling them, so a preset must not overwrite them.
   */
  const applyPreset = (preset: ThemePreset) =>
    setForm((prev) => ({
      ...preset.values,
      brandName: prev.brandName,
      logoUrl: prev.logoUrl,
      layoutStyle: preset.values.layoutStyle || prev.layoutStyle,
    }));

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

        {presets && presets.length > 0 && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>Start from a preset</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {presets.map((preset) => (
                <Tooltip key={preset.key} title={preset.description}>
                  <Chip
                    label={preset.label}
                    variant="outlined"
                    onClick={() => applyPreset(preset)}
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
            </Stack>
            <Typography variant="caption" color="text.secondary">
              Fills the fields below — nothing is saved until you press save. Your brand name and
              logo are left as they are.
            </Typography>
          </Box>
        )}

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
