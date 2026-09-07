import React, { useEffect, useMemo, useState } from 'react';
import { useDateTime } from '../../providers/UiConfigProvider';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  InputAdornment,
  MenuItem,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Add,
  ArrowBack,
  ArrowDownward,
  ArrowUpward,
  Check,
  Close,
  Edit,
  ErrorOutline,
  Home,
  Palette,
  RestartAlt,
  Search,
  Visibility,
  VisibilityOff,
} from '@mui/icons-material';
import DynamicIcon from '../../components/DynamicIcon';
import { alpha } from '@mui/material/styles';
import { apiErrorMessage } from '../../services/apiError';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';
import {
  ALL_MODULES,
  BUTTON_STYLES,
  changeTenantStatus,
  COLOR_MODES,
  DENSITIES,
  createTenant,
  CreateTenantCommand,
  fetchTenants,
  HEX_COLOR,
  HORIZONTAL_MODULES,
  LAYOUT_STYLES,
  moduleLabel,
  fetchMenuCatalogue,
  fetchTenant,
  fetchTenantThemeStatus,
  fetchThemePresets,
  brandingFromPreset,
  isNoopOverride,
  MenuCatalogueEntry,
  previewMenu,
  setTenantNavigation,
  setTenantBranding,
  setTenantModules,
  TenantMenuOverride,
  ThemePreset,
  updateTenant,
  UpdateTenantCommand,
  STYLE_HELP,
  Tenant,
  TenantBranding,
  TENANT_STATUS_HELP,
  TENANT_STATUSES,
  TenantStatus,
  UI_STYLES,
  Vertical,
  VERTICAL_MODULES,
  VERTICALS,
} from '../../services/tenantApi';

/**
 * Operator's view over every tenant on the platform. Creating one provisions a schema per service
 * — tenant-service publishes to `tenant.events` and each service runs Flyway against the new
 * schema — so this screen is the whole onboarding flow, not a record of one done elsewhere.
 *
 * Only a SUPER_ADMIN of the `platform` tenant may use any of it. That is enforced by
 * tenant-service, not here; this screen just explains the 403 rather than showing an empty table.
 */

/**
 * PENDING is deliberately missing: it means "created but not yet provisioned", which the platform
 * sets and clears itself. Offering it as a button let an operator move a live, serving tenant into
 * a state that describes something untrue and changes nothing.
 */
const OPERATOR_SETTABLE_STATUSES: TenantStatus[] =
  TENANT_STATUSES.filter((status) => status !== 'PENDING');

/**
 * The colour that identifies a tenant's row, or null when it has chosen none.
 *
 * Trimmed to six digits: a tenant may store `#RRGGBBAA`, and MUI's `alpha()` cannot parse the
 * eight-digit form — it would throw and take the whole list down with it.
 */
const rowAccent = (tenant: Tenant): string | null => {
  const candidate = tenant.branding?.primaryColor || tenant.branding?.accentColor;
  return candidate && HEX_COLOR.test(candidate) ? candidate.slice(0, 7) : null;
};

const STATUS_COLOR: Record<TenantStatus, 'success' | 'warning' | 'error' | 'default'> = {
  ACTIVE: 'success',
  PENDING: 'warning',
  SUSPENDED: 'error',
  ARCHIVED: 'default',
};

/** "Acme Builders" -> "acmebuilders", matching TenantKey.normalise so the operator sees the key
 *  the platform will actually store before creating it. */
const previewTenantKey = (name: string) =>
  name.trim().toLowerCase().replace(/[^a-z0-9]/g, '');

const TENANT_KEY_PATTERN = /^[a-z][a-z0-9]{1,30}$/;

/** Mirrors TenantKey.RESERVED — a key that would name infrastructure rather than a tenant. */
const RESERVED_KEYS = new Set([
  'www', 'api', 'admin', 'app', 'mail', 'static', 'assets', 'cdn',
  'mysql', 'redis', 'kafka', 'grafana', 'prometheus', 'public', 'information',
]);

/** Mirrors the hostname pattern tenant-service validates a custom domain against. */
const HOSTNAME = /^(?!-)[a-zA-Z0-9-]{1,63}(\.(?!-)[a-zA-Z0-9-]{1,63})+$/;

/**
 * A readable name for the landing path, for the branding preview's caption.
 *
 * Derived from the path rather than looked up in the catalogue: the preview wants a word to put in a
 * mock page header, and threading the catalogue through the branding step to get a perfect one would
 * couple two steps that otherwise share nothing.
 */
const landingLabelFor = (landingPath: string | null): string | undefined => {
  if (!landingPath) return undefined;
  const segment = landingPath.split('/').filter(Boolean).pop();
  if (!segment) return undefined;
  return segment.replace(/-/g, ' ').replace(/^./, (c) => c.toUpperCase());
};

const tenantKeyError = (key: string): string | null => {
  if (!TENANT_KEY_PATTERN.test(key)) {
    return '2-31 characters, lowercase letters and digits, starting with a letter.';
  }
  if (RESERVED_KEYS.has(key)) return `"${key}" is reserved.`;
  return null;
};

/** One colour, as a native swatch picker beside the hex the API actually receives. Typing the hex
 *  is kept because operators paste brand colours far more often than they pick them. */
const ColorField: React.FC<{
  label: string;
  value: string;
  onChange: (value: string) => void;
  helperText: string;
}> = ({ label, value, onChange, helperText }) => {
  const set = value !== '';
  const invalid = set && !HEX_COLOR.test(value);
  return (
    <Stack direction="row" spacing={1} alignItems="flex-start">
      {/* An unset colour shows a muted "no colour" tile rather than a swatch of the picker's
          default — a blue chip beside an empty field reads as "primary is already blue". */}
      <Tooltip title={set ? 'Change colour' : 'Not set — inherits the platform theme'}>
        <Box
          component="label"
          sx={{
            width: 48, height: 40, mt: 1, flexShrink: 0, cursor: 'pointer', borderRadius: 1,
            border: '1px solid', borderColor: set ? 'divider' : 'text.disabled',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            bgcolor: set && !invalid ? value : 'transparent',
            backgroundImage: set
              ? 'none'
              : 'repeating-linear-gradient(45deg, rgba(128,128,128,.25) 0 4px, transparent 4px 8px)',
          }}
        >
          <Box
            component="input"
            type="color"
            aria-label={`${label} colour`}
            value={HEX_COLOR.test(value) ? value.slice(0, 7) : '#1e88e5'}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
            sx={{ opacity: 0, width: 0, height: 0, border: 0, p: 0 }}
          />
        </Box>
      </Tooltip>
      <TextField
        fullWidth
        size="small"
        label={label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        error={invalid}
        helperText={invalid ? 'Use a hex colour like #1E88E5.' : helperText}
        placeholder="Platform default"
        InputProps={{
          endAdornment: set ? (
            <InputAdornment position="end">
              <Tooltip title="Clear — inherit the platform theme">
                <IconButton size="small" edge="end" onClick={() => onChange('')}>
                  <Close fontSize="small" />
                </IconButton>
              </Tooltip>
            </InputAdornment>
          ) : null,
        }}
      />
    </Stack>
  );
};

/** A closed-set style choice. The description under it comes from STYLE_HELP, so an operator does
 *  not have to guess what "flat" or "elevated" will do to a tenant they cannot see yet. */
const StyleSelect: React.FC<{
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
  inheritLabel: string;
}> = ({ label, value, options, onChange, inheritLabel }) => (
  <TextField
    select
    fullWidth
    size="small"
    label={label}
    value={value}
    onChange={(e) => onChange(e.target.value)}
    helperText={value === '' ? inheritLabel : STYLE_HELP[value]}
  >
    <MenuItem value=""><em>{inheritLabel}</em></MenuItem>
    {options.map((option) => (
      <MenuItem key={option} value={option}>{option}</MenuItem>
    ))}
  </TextField>
);

/**
 * The blank branding form. Every field empty means "send no branding at all".
 *
 * All strings, including `borderRadius`, because a half-typed number in a text field is not a
 * number and a form that stores it as one has to invent a value for "the operator has cleared this
 * and is about to type something else". Converted once, on the way out, in `toBrandingPayload`.
 */
const EMPTY_BRANDING = {
  logoUrl: '', brandName: '', primaryColor: '', accentColor: '', surfaceColor: '',
  sidebarColor: '', colorMode: '', uiStyle: '', buttonStyle: '', layoutStyle: '',
  density: '', borderRadius: '', fontFamily: '', presetKey: '',
};

type BrandingForm = typeof EMPTY_BRANDING;

/** Fields that are numbers on the wire and strings in the form. */
const NUMERIC_BRANDING_FIELDS = ['borderRadius'];

/** A saved tenant's branding back into form state; nulls become the empty string the fields use. */
const toBrandingForm = (branding: TenantBranding | null): BrandingForm => ({
  ...EMPTY_BRANDING,
  ...Object.fromEntries(
    Object.entries(branding || {})
      .filter(([key]) => key in EMPTY_BRANDING)
      .map(([key, value]) => [key, value === null || value === undefined ? '' : String(value)])
  ),
});

/**
 * Drops the empty fields, so an untouched form sends nothing rather than a wall of nulls.
 *
 * `presetKey` alone is not enough to count as branding: it is bookkeeping about where a palette came
 * from, and sending it with every colour blank would store "started from Ocean" on a tenant that is
 * on the platform theme.
 */
const toBrandingPayload = (form: BrandingForm): TenantBranding | undefined => {
  const entries = Object.entries(form).filter(([, value]) => value.trim() !== '');
  const meaningful = entries.filter(([key]) => key !== 'presetKey');
  if (meaningful.length === 0) return undefined;

  return Object.fromEntries(
    entries.map(([key, value]) => [
      key,
      NUMERIC_BRANDING_FIELDS.includes(key) ? Number(value) : value.trim(),
    ])
  ) as TenantBranding;
};

/** Matches what `TenantBranding.validate()` accepts, so the operator is not told by a 400. */
const brandingValid = (form: BrandingForm) => {
  const radius = form.borderRadius.trim();
  const radiusOk =
    radius === '' || (/^\d+$/.test(radius) && Number(radius) >= 0 && Number(radius) <= 32);
  return (
    [form.primaryColor, form.accentColor, form.surfaceColor, form.sidebarColor]
      .every((value) => value === '' || HEX_COLOR.test(value)) &&
    radiusOk &&
    form.logoUrl.length <= 500 &&
    form.fontFamily.length <= 200 &&
    form.brandName.length <= 60
  );
};

/**
 * Reorders one item within the effective menu, returning the sort-order overrides that produce it.
 *
 * The positions themselves are reused rather than renumbered: the list's own sort orders are
 * redealt in the new sequence, so an item only gains an override when it actually ends up somewhere
 * the catalogue did not put it. Renumbering 0..n instead would write a row for every item on the
 * first nudge, and the override table would stop meaning "the operator decided this".
 */
const reorderOverrides = (
  effective: MenuCatalogueEntry[],
  catalogue: MenuCatalogueEntry[],
  from: number,
  to: number
): Map<string, number | null> => {
  const moved = [...effective];
  const [item] = moved.splice(from, 1);
  moved.splice(to, 0, item);

  const positions = effective.map((entry) => entry.sortOrder).sort((a, b) => a - b);
  const catalogueOrder = new Map(catalogue.map((entry) => [entry.itemKey, entry.sortOrder]));

  const result = new Map<string, number | null>();
  moved.forEach((entry, index) => {
    const position = positions[index];
    // null means "drop the override" — back to wherever the catalogue puts it.
    result.set(entry.itemKey, catalogueOrder.get(entry.itemKey) === position ? null : position);
  });
  return result;
};

/**
 * Module checkboxes, and what switching one off takes away.
 *
 * Modules and the menu were unrelated systems until recently, which is how a fee-collection tenant
 * ended up with Bookings in its navigation pointing at routes the gateway refuses. This is the
 * decision that actually removes things: a module the tenant does not have is closed at the gateway,
 * not merely absent from the sidebar. Reshaping what survives is the next step's job, and the two
 * are deliberately no longer the same screen — presenting them as equal checkbox trees invited
 * hiding a tab instead of removing the module, which leaves the API reachable.
 */
const ModulesStep: React.FC<{
  vertical: Vertical;
  modules: Set<string>;
  onModules: (modules: Set<string>) => void;
  /** Modules already on that this vertical does not list; kept visible so they can be turned off. */
  extraModules?: string[];
}> = ({ vertical, modules, onModules, extraModules = [] }) => {
  const { data: catalogue } = useQuery({
    queryKey: ['menu-catalogue'],
    queryFn: fetchMenuCatalogue,
    staleTime: 5 * 60 * 1000,
  });

  const choosable = useMemo(() => {
    const own = VERTICAL_MODULES[vertical] || [];
    return [...own, ...extraModules.filter((key) => !own.includes(key))];
  }, [vertical, extraModules]);

  const toggleModule = (key: string) => {
    const next = new Set(modules);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    onModules(next);
  };

  /**
   * What each module brings, as a deduplicated list of screen names.
   *
   * Deduplicated because most areas have both a member-facing screen and an admin one under the
   * same label — `bookings` alone would otherwise read "Services, Live Tracking, Categories,
   * Services, Bookings, Live Tracking", which looks like a rendering bug rather than a list.
   */
  const itemsByModule = useMemo(() => {
    const map = new Map<string, string[]>();
    (catalogue || []).forEach((item) => {
      if (!item.requiredModule) return;
      const list = map.get(item.requiredModule) || [];
      if (!list.includes(item.label)) list.push(item.label);
      map.set(item.requiredModule, list);
    });
    return map;
  }, [catalogue]);

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
        {VERTICALS.find((v) => v.value === vertical)?.label || vertical} modules
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        What the tenant has bought. A module they do not have is refused at the gateway with a 404,
        and its screens disappear from their navigation with it.
      </Typography>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 1 }}>
        {choosable.map((key) => {
          const items = itemsByModule.get(key) || [];
          return (
            <Box
              key={key}
              sx={{
                border: '1px solid', borderColor: modules.has(key) ? 'primary.light' : 'divider',
                borderRadius: 1, p: 1.25,
                bgcolor: (theme) =>
                  modules.has(key) ? alpha(theme.palette.primary.main, 0.04) : 'transparent',
              }}
            >
              <FormControlLabel
                control={<Checkbox checked={modules.has(key)} onChange={() => toggleModule(key)} />}
                label={moduleLabel(key)}
              />
              {items.length > 0 && (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ display: 'block', pl: 4, mt: -0.5 }}
                >
                  {items.join(', ')}
                </Typography>
              )}
            </Box>
          );
        })}
      </Box>

      <Divider sx={{ my: 2 }} />

      <Typography variant="subtitle2" sx={{ mb: 1 }}>Always on</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Every tenant has these whatever they bought — sign-in, users, payments and the rest. Not a
        choice, so not a checkbox.
      </Typography>
      <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
        {HORIZONTAL_MODULES.map((key) => (
          <Chip key={key} size="small" variant="outlined" label={moduleLabel(key)} />
        ))}
      </Stack>
    </Box>
  );
};

/**
 * The tenant's navigation, as the operator shapes it.
 *
 * Hiding used to be all this could do. It was the least useful of the three things an operator
 * actually wants: they also want the sidebar in a particular order, and a tab named in the
 * customer's own vocabulary — "Bookings" is "Site Visits" to one tenant and "Inspections" to
 * another. All three live here, plus the screen the console opens on.
 *
 * What it cannot do is add anything back. Items whose module is off are listed greyed at the bottom,
 * because "why is that tab missing" is the question this screen exists to answer — but the fix for
 * one of those is the modules step, not here.
 */
const NavigationEditor: React.FC<{
  modules: Set<string>;
  overrides: Map<string, TenantMenuOverride>;
  landingPath: string | null;
  onOverrides: (overrides: Map<string, TenantMenuOverride>) => void;
  /** The key travels with the path so the save can name the item for server-side validation. */
  onLandingPath: (path: string | null, itemKey: string | null) => void;
}> = ({ modules, overrides, landingPath, onOverrides, onLandingPath }) => {
  const { data: catalogue, isLoading, isError } = useQuery({
    queryKey: ['menu-catalogue'],
    queryFn: fetchMenuCatalogue,
    staleTime: 5 * 60 * 1000,
  });

  const overrideList = useMemo(() => [...overrides.values()], [overrides]);

  const effective = useMemo(
    () => (catalogue ? previewMenu(catalogue, [...modules], overrideList) : []),
    [catalogue, modules, overrideList]
  );

  /** Items whose module is on but which the operator has hidden — the only ones that can come back. */
  const hidden = useMemo(
    () =>
      (catalogue || []).filter(
        (item) =>
          overrides.get(item.itemKey)?.visible === false &&
          (item.requiredModule === null || modules.has(item.requiredModule))
      ),
    [catalogue, overrides, modules]
  );

  const removedByModule = useMemo(
    () =>
      (catalogue || []).filter(
        (item) => item.requiredModule !== null && !modules.has(item.requiredModule)
      ),
    [catalogue, modules]
  );

  const patch = (itemKey: string, change: Partial<TenantMenuOverride>) => {
    const next = new Map(overrides);
    const merged: TenantMenuOverride = {
      itemKey,
      visible: true,
      labelOverride: null,
      sortOrder: null,
      ...(next.get(itemKey) || {}),
      ...change,
    };
    // A row that says nothing is dropped rather than stored — the server does the same, and keeping
    // it here would make "customised" light up for an item nobody has touched.
    if (isNoopOverride(merged)) next.delete(itemKey);
    else next.set(itemKey, merged);
    onOverrides(next);
  };

  const setHidden = (item: MenuCatalogueEntry, hide: boolean) => {
    patch(item.itemKey, { visible: !hide });
    // Hiding the screen the console opens on would leave the tenant landing somewhere their own
    // navigation no longer offers, so the landing choice goes back to the dashboard with it. The
    // server refuses the combination outright; clearing it here means the operator never has to
    // untangle a rejected save.
    if (hide && landingPath === item.path) onLandingPath(null, null);
  };

  const move = (index: number, delta: number) => {
    const to = index + delta;
    if (!catalogue || to < 0 || to >= effective.length) return;
    const orders = reorderOverrides(effective, catalogue, index, to);
    const next = new Map(overrides);
    orders.forEach((sortOrder, itemKey) => {
      const merged: TenantMenuOverride = {
        itemKey,
        visible: true,
        labelOverride: null,
        ...(next.get(itemKey) || {}),
        sortOrder,
      };
      if (isNoopOverride(merged)) next.delete(itemKey);
      else next.set(itemKey, merged);
    });
    onOverrides(next);
  };

  if (isLoading) return <CircularProgress size={20} />;
  if (isError || !catalogue) {
    return (
      <Alert severity="warning">
        Could not load the menu catalogue, so the navigation cannot be edited. The tenant will get
        the default menu for its modules.
      </Alert>
    );
  }

  const customisedCount = overrideList.filter((o) => !isNoopOverride(o)).length;

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 1 }}>
        <Box>
          <Typography variant="subtitle2">Sidebar</Typography>
          <Typography variant="body2" color="text.secondary">
            Reorder, rename, or hide items, and pick the screen this tenant opens on.
          </Typography>
        </Box>
        {customisedCount > 0 && (
          <Button
            size="small"
            startIcon={<RestartAlt />}
            onClick={() => { onOverrides(new Map()); onLandingPath(null, null); }}
          >
            Reset all
          </Button>
        )}
      </Stack>

      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: 40 }} />
            <TableCell>Item</TableCell>
            <TableCell sx={{ width: 200 }}>Shown as</TableCell>
            <TableCell align="center" sx={{ width: 96 }}>
              <Tooltip title="Open the tenant's console on this screen">
                <span>Landing</span>
              </Tooltip>
            </TableCell>
            <TableCell align="right" sx={{ width: 120 }}>Order</TableCell>
            <TableCell align="center" sx={{ width: 64 }}>Show</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {effective.map((item, index) => {
            const override = overrides.get(item.itemKey);
            return (
              <TableRow key={item.itemKey} hover>
                <TableCell><DynamicIcon name={item.icon} fontSize="small" /></TableCell>
                <TableCell>
                  <Typography variant="body2">{item.label}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {item.section}
                    {item.requiredModule && ` · ${item.requiredModule}`}
                  </Typography>
                </TableCell>
                <TableCell>
                  <TextField
                    size="small"
                    fullWidth
                    variant="standard"
                    value={override?.labelOverride || ''}
                    placeholder={item.label}
                    onChange={(e) =>
                      patch(item.itemKey, { labelOverride: e.target.value || null })
                    }
                    inputProps={{ maxLength: 120 }}
                  />
                </TableCell>
                <TableCell align="center">
                  <Tooltip
                    title={
                      landingPath === item.path
                        ? 'This tenant opens here'
                        : 'Open this tenant here on sign-in'
                    }
                  >
                    <IconButton
                      size="small"
                      color={landingPath === item.path ? 'primary' : 'default'}
                      onClick={() =>
                        landingPath === item.path
                          ? onLandingPath(null, null)
                          : onLandingPath(item.path, item.itemKey)
                      }
                    >
                      <Home fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    disabled={index === 0}
                    onClick={() => move(index, -1)}
                  >
                    <ArrowUpward fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    disabled={index === effective.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    <ArrowDownward fontSize="small" />
                  </IconButton>
                </TableCell>
                <TableCell align="center">
                  <Tooltip title="Hide this item for this tenant only">
                    <IconButton size="small" onClick={() => setHidden(item, true)}>
                      <VisibilityOff fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {hidden.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary">
            Hidden for this tenant — the module is on, so these can come back
          </Typography>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
            {hidden.map((item) => (
              <Chip
                key={item.itemKey}
                size="small"
                icon={<Visibility fontSize="small" />}
                label={item.label}
                onClick={() => setHidden(item, false)}
              />
            ))}
          </Stack>
        </Box>
      )}

      {removedByModule.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary">
            Not included — the module is off, so these cannot be switched on here
          </Typography>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
            {removedByModule.map((item) => (
              <Tooltip
                key={item.itemKey}
                title={`Needs the ${moduleLabel(item.requiredModule as string)} module`}
              >
                <Chip
                  size="small"
                  variant="outlined"
                  sx={{ opacity: 0.6 }}
                  label={`${item.label} · ${item.requiredModule}`}
                />
              </Tooltip>
            ))}
          </Stack>
        </Box>
      )}
    </Box>
  );
};

/**
 * A miniature of the tenant's shell in the chosen branding.
 *
 * Thirteen controls otherwise describe a result the operator cannot see until they sign in as the
 * tenant — which they cannot do. Deliberately approximate: it shows the decisions that differ
 * visibly (colours, density, radius, font, sidebar placement) rather than pretending to be a
 * faithful render of the real console.
 */
const BrandingPreview: React.FC<{
  form: BrandingForm;
  name: string;
  /** The screen the tenant opens on, so the preview shows the page they will actually land on. */
  landingLabel?: string;
}> = ({ form, name, landingLabel }) => {
  const primary = HEX_COLOR.test(form.primaryColor) ? form.primaryColor : '#1e88e5';
  const accent = HEX_COLOR.test(form.accentColor) ? form.accentColor : '#9c27b0';
  const sidebar = HEX_COLOR.test(form.sidebarColor) ? form.sidebarColor : '#111827';
  const dark = form.colorMode === 'dark';
  const pad = form.density === 'compact' ? 0.5 : form.density === 'spacious' ? 1.75 : 1;
  // An explicit radius wins; without one the surface style still implies a rounding, which is what
  // the preview showed before there was a radius field to set.
  const radius =
    form.borderRadius.trim() !== '' && /^\d+$/.test(form.borderRadius.trim())
      ? `${Math.min(Number(form.borderRadius), 32)}px`
      : form.uiStyle === 'flat' ? 0 : form.uiStyle === 'elevated' ? 3 : 1;
  const shadow = form.uiStyle === 'elevated' ? 6 : form.uiStyle === 'flat' ? 0 : 1;
  // The tenant's own surface colour beats the light/dark default. Without this the one control that
  // names a background was the only branding field the preview ignored.
  const surface = HEX_COLOR.test(form.surfaceColor)
    ? form.surfaceColor
    : dark ? '#1e293b' : '#fff';
  const page = HEX_COLOR.test(form.surfaceColor)
    ? alpha(form.surfaceColor.slice(0, 7), dark ? 0.6 : 0.45)
    : dark ? '#0f172a' : '#f4f6f8';
  const font = form.fontFamily.trim() || 'inherit';
  const wordmark = form.brandName.trim() || name.trim() || 'Tenant';
  const topbar = form.layoutStyle === 'topbar';
  const railRight = form.layoutStyle === 'sidebar-right';

  const rail = (horizontal: boolean) => (
    <Box
      sx={{
        bgcolor: sidebar,
        ...(horizontal
          ? { height: 30, width: '100%', display: 'flex', alignItems: 'center', px: 1, gap: 1 }
          : { width: 64, flexShrink: 0, p: 1 }),
      }}
    >
      {form.logoUrl.trim() !== '' ? (
        <Box
          component="img"
          src={form.logoUrl}
          alt=""
          sx={{ height: 16, maxWidth: horizontal ? 60 : '100%', objectFit: 'contain' }}
        />
      ) : (
        <Typography noWrap sx={{ color: 'common.white', fontSize: 9, opacity: 0.9, fontFamily: font }}>
          {wordmark}
        </Typography>
      )}
      {!horizontal && (
        <Stack spacing={0.5} sx={{ mt: 1 }}>
          {[0.9, 0.5, 0.5].map((opacity, i) => (
            <Box key={i} sx={{ height: 5, borderRadius: 0.5, bgcolor: i === 0 ? primary : 'grey.600', opacity }} />
          ))}
        </Stack>
      )}
    </Box>
  );

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 1 }}>Preview</Typography>
      <Box
        sx={{
          border: '1px solid', borderColor: 'divider', borderRadius: 1, overflow: 'hidden',
          bgcolor: page, fontFamily: font,
        }}
      >
        {topbar && rail(true)}
        <Box sx={{ display: 'flex', flexDirection: railRight ? 'row-reverse' : 'row', minHeight: 132 }}>
          {!topbar && rail(false)}
          <Box sx={{ flex: 1, p: pad }}>
            <Box
              sx={{
                p: pad, mb: pad, borderRadius: radius, boxShadow: shadow,
                bgcolor: surface,
                border: form.uiStyle === 'flat' ? '1px solid' : 'none',
                borderColor: 'divider',
              }}
            >
              <Typography
                sx={{ fontSize: 10, color: dark ? 'grey.300' : 'grey.800', mb: 0.5, fontFamily: font }}
              >
                {landingLabel || 'Dashboard'}
              </Typography>
              <Box sx={{ height: 4, width: '70%', borderRadius: 2, bgcolor: dark ? 'grey.700' : 'grey.300', mb: 0.5 }} />
              <Box sx={{ height: 4, width: '45%', borderRadius: 2, bgcolor: dark ? 'grey.700' : 'grey.300' }} />
            </Box>
            <Stack direction="row" spacing={1}>
              <Box
                sx={{
                  px: 1.2, py: 0.4, fontSize: 9, borderRadius: radius, color: 'common.white',
                  background: form.buttonStyle === 'gradient'
                    ? `linear-gradient(135deg, ${primary}, ${accent})`
                    : primary,
                  ...(form.buttonStyle === 'outlined' && {
                    background: 'transparent', color: primary,
                    border: '1px solid', borderColor: primary,
                  }),
                }}
              >
                Primary
              </Box>
              <Box
                sx={{
                  px: 1.2, py: 0.4, fontSize: 9, borderRadius: radius,
                  color: accent, border: '1px solid', borderColor: accent,
                }}
              >
                Accent
              </Box>
            </Stack>
          </Box>
        </Box>
      </Box>
      <Typography variant="caption" color="text.secondary">
        Approximate — shows the choices that change how the shell looks.
      </Typography>
    </Box>
  );
};

/**
 * The shipped palettes, as swatches to start from.
 *
 * This is the answer to the actual complaint about this screen: choosing how a tenant looks meant
 * inventing six hex codes, while the tenants' own theme screen had offered these presets all along.
 * Picking one fills the form and leaves it editable — the operator still saves, and can still nudge
 * any single value afterwards, exactly as applying a preset works everywhere else in the console.
 */
const PresetPicker: React.FC<{
  branding: TenantBranding;
  onApply: (branding: TenantBranding) => void;
  currentKey: string;
}> = ({ branding, onApply, currentKey }) => {
  const { data: presets, isLoading, isError } = useQuery({
    queryKey: ['theme-presets'],
    queryFn: fetchThemePresets,
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading) return <CircularProgress size={20} />;
  // A soft failure: the hex fields below still work, so this is a missing shortcut rather than a
  // broken form, and saying so beats an error that implies branding cannot be set at all.
  if (isError || !presets?.length) {
    return (
      <Alert severity="info" sx={{ mb: 2 }}>
        Presets could not be loaded. The colours below still apply.
      </Alert>
    );
  }

  return (
    <Box sx={{ mb: 3 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
        <Palette fontSize="small" color="action" />
        <Typography variant="subtitle2">Start from a preset</Typography>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        These are the palettes the platform ships. Picking one fills in the fields below — nudge
        anything afterwards.
      </Typography>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        {presets.map((preset: ThemePreset) => {
          const selected = preset.key === currentKey;
          const swatch = [
            preset.values.primaryColor,
            preset.values.accentColor,
            preset.values.sidebarColor,
          ].filter(Boolean) as string[];
          return (
            <Tooltip key={preset.key} title={preset.description || preset.label}>
              <Box
                component="button"
                type="button"
                onClick={() => onApply(brandingFromPreset(preset, branding))}
                sx={{
                  cursor: 'pointer', textAlign: 'left', p: 1, borderRadius: 1,
                  border: '2px solid',
                  borderColor: selected ? 'primary.main' : 'divider',
                  bgcolor: 'background.paper', minWidth: 132,
                }}
              >
                <Stack direction="row" spacing={0.5} sx={{ mb: 0.75 }}>
                  {swatch.map((color) => (
                    <Box
                      key={color}
                      sx={{ width: 20, height: 20, borderRadius: 0.5, bgcolor: color }}
                    />
                  ))}
                </Stack>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  {selected && <Check fontSize="small" color="primary" />}
                  <Typography variant="caption" fontWeight={selected ? 700 : 500}>
                    {preset.label}
                  </Typography>
                </Stack>
              </Box>
            </Tooltip>
          );
        })}
      </Stack>
    </Box>
  );
};

const BrandingFields: React.FC<{
  form: BrandingForm;
  onChange: (form: BrandingForm) => void;
  /** Wordmark shown in the preview when no logo is given. */
  name: string;
  /** The screen the tenant opens on, shown inside the preview. */
  landingLabel?: string;
}> = ({ form, onChange, name, landingLabel }) => {
  const set = (patch: Partial<BrandingForm>) => onChange({ ...form, ...patch });

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 3 }}>
      <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Optional. Anything left blank uses the shipped platform default, and the tenant can change
        all of it later in their own Theme &amp; UI style screen.
      </Typography>

      <PresetPicker
        branding={toBrandingPayload(form) || {}}
        currentKey={form.presetKey}
        onApply={(branding) => onChange({ ...form, ...toBrandingForm(branding) })}
      />

      <Divider sx={{ mb: 2 }} />

      <TextField
        fullWidth
        size="small"
        label="Brand name"
        value={form.brandName}
        onChange={(e) => set({ brandName: e.target.value })}
        sx={{ mb: 2 }}
        error={form.brandName.length > 60}
        helperText={
          form.brandName.length > 60
            ? 'Maximum 60 characters.'
            : `The wordmark in their shell. Blank uses the tenant's name${
                name.trim() ? ` — "${name.trim()}"` : ''
              }.`
        }
      />

      <TextField
        fullWidth
        size="small"
        label="Logo URL"
        value={form.logoUrl}
        onChange={(e) => set({ logoUrl: e.target.value })}
        sx={{ mb: 2 }}
        error={form.logoUrl.length > 500}
        helperText={
          form.logoUrl.length > 500
            ? 'Maximum 500 characters.'
            : 'Shown beside the wordmark in the shell. Absolute or app-relative.'
        }
      />
      <Typography variant="subtitle2" sx={{ mb: 1 }}>Colours</Typography>
      <Stack spacing={1.5} sx={{ mb: 2 }}>
        <ColorField
          label="Primary"
          value={form.primaryColor}
          onChange={(primaryColor) => set({ primaryColor })}
          helperText="Buttons, links and active navigation."
        />
        <ColorField
          label="Accent"
          value={form.accentColor}
          onChange={(accentColor) => set({ accentColor })}
          helperText="Highlights and secondary actions."
        />
        <ColorField
          label="Surface"
          value={form.surfaceColor}
          onChange={(surfaceColor) => set({ surfaceColor })}
          helperText="Cards and panels — the background content sits on."
        />
        <ColorField
          label="Sidebar"
          value={form.sidebarColor}
          onChange={(sidebarColor) => set({ sidebarColor })}
          helperText="The navigation rail, separate from the page surface."
        />
      </Stack>

      <Typography variant="subtitle2" sx={{ mb: 1 }}>UI style</Typography>
      <Stack spacing={2}>
        <StyleSelect
          label="Colour mode"
          value={form.colorMode}
          options={COLOR_MODES}
          onChange={(colorMode) => set({ colorMode })}
          inheritLabel="Platform default (system)"
        />
        <StyleSelect
          label="Surface style"
          value={form.uiStyle}
          options={UI_STYLES}
          onChange={(uiStyle) => set({ uiStyle })}
          inheritLabel="Platform default"
        />
        <StyleSelect
          label="Button style"
          value={form.buttonStyle}
          options={BUTTON_STYLES}
          onChange={(buttonStyle) => set({ buttonStyle })}
          inheritLabel="Platform default"
        />
        <StyleSelect
          label="Layout"
          value={form.layoutStyle}
          options={LAYOUT_STYLES}
          onChange={(layoutStyle) => set({ layoutStyle })}
          inheritLabel="Platform default"
        />
        <StyleSelect
          label="Density"
          value={form.density}
          options={DENSITIES}
          onChange={(density) => set({ density })}
          inheritLabel="Platform default"
        />
        <TextField
          fullWidth
          size="small"
          label="Corner radius"
          value={form.borderRadius}
          onChange={(e) => set({ borderRadius: e.target.value.replace(/[^0-9]/g, '') })}
          error={form.borderRadius !== '' && Number(form.borderRadius) > 32}
          placeholder="Platform default"
          InputProps={{ endAdornment: <InputAdornment position="end">px</InputAdornment> }}
          helperText={
            form.borderRadius !== '' && Number(form.borderRadius) > 32
              ? 'Maximum 32px — past that the shell stops rendering it as a corner.'
              : '0 is square. The shipped theme uses 12.'
          }
        />
        <TextField
          fullWidth
          size="small"
          label="Font family"
          value={form.fontFamily}
          onChange={(e) => set({ fontFamily: e.target.value })}
          error={form.fontFamily.length > 200}
          placeholder="Platform default"
          helperText={
            form.fontFamily.length > 200
              ? 'Maximum 200 characters.'
              : 'A CSS stack, e.g. Inter, system-ui, sans-serif. The tenant must be able to load it.'
          }
        />
      </Stack>
      </Box>

      {/* Sticky so the preview stays in view while the operator works down the controls. */}
      <Box sx={{ position: { md: 'sticky' }, top: 0, alignSelf: 'start' }}>
        <BrandingPreview form={form} name={name} landingLabel={landingLabel} />
      </Box>
    </Box>
  );
};

/**
 * Onboarding a tenant, in the four decisions it actually is.
 *
 * Identity, then what they bought, then how their navigation reads, then how it looks. The same four
 * appear as independently-savable sections on the tenant's own page, so the operator learns one
 * model rather than one for creating and another for editing.
 *
 * Every step stays mounted while hidden: switching tabs must not discard a half-filled form, and the
 * branding step in particular is where someone pastes a brand colour they cannot easily find again.
 */
const NewTenantDialog: React.FC<{
  open: boolean;
  onClose: () => void;
  onCreated: (tenant: Tenant) => void;
}> = ({ open, onClose, onCreated }) => {
  const [name, setName] = useState('');
  const [keyOverride, setKeyOverride] = useState<string | null>(null);
  const [contactEmail, setContactEmail] = useState('');
  const [customDomain, setCustomDomain] = useState('');
  const [vertical, setVertical] = useState<Vertical>('CIVIL_MARKETPLACE');
  const [branding, setBranding] = useState<BrandingForm>(EMPTY_BRANDING);
  const [modules, setModules] = useState<Set<string>>(
    new Set(VERTICAL_MODULES.CIVIL_MARKETPLACE)
  );
  const [overrides, setOverrides] = useState<Map<string, TenantMenuOverride>>(new Map());
  const [landingPath, setLandingPath] = useState<string | null>(null);
  const [tab, setTab] = useState(0);

  const reset = () => {
    setName('');
    setKeyOverride(null);
    setContactEmail('');
    setCustomDomain('');
    setVertical('CIVIL_MARKETPLACE');
    setBranding(EMPTY_BRANDING);
    setModules(new Set(VERTICAL_MODULES.CIVIL_MARKETPLACE));
    setOverrides(new Map());
    setLandingPath(null);
    setTab(0);
  };

  // Follows the name until the operator types a key of their own, then stops — re-deriving it
  // after that would silently overwrite a deliberate choice on the next keystroke of the name.
  const tenantKey = keyOverride ?? previewTenantKey(name);
  const keyError = tenantKey === '' ? null : tenantKeyError(tenantKey);
  const emailValid = /^\S+@\S+\.\S+$/.test(contactEmail.trim());
  const domainValid = customDomain.trim() === '' || HOSTNAME.test(customDomain.trim());

  // Named rather than a bare boolean: a greyed-out Create button with no explanation is the most
  // common way a form wastes someone's time, especially when the offending field is on a tab they
  // cannot see.
  const blocker =
    name.trim() === '' ? { tab: 0, message: 'Enter a name.' }
    : tenantKey === '' ? { tab: 0, message: 'Enter a tenant key.' }
    : keyError ? { tab: 0, message: keyError }
    : contactEmail.trim() === '' ? { tab: 0, message: 'Enter a contact email.' }
    : !emailValid ? { tab: 0, message: 'That contact email is not valid.' }
    : !domainValid
      ? { tab: 0, message: 'A custom domain is a hostname, with no scheme or path.' }
    : !modules.has('auth') && VERTICAL_MODULES[vertical].includes('auth')
      ? { tab: 1, message: 'A tenant needs the auth module.' }
    : !brandingValid(branding) ? { tab: 3, message: 'Check the branding values.' }
    : null;
  const valid = blocker === null;

  const create = useMutation({
    mutationFn: (command: CreateTenantCommand) => createTenant(command),
    onSuccess: (tenant) => {
      reset();
      onCreated(tenant);
    },
  });

  const close = () => {
    if (create.isPending) return;
    create.reset();
    onClose();
  };

  const chosenVertical = VERTICALS.find((v) => v.value === vertical);

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="md">
      <DialogTitle>New tenant</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Creating a tenant provisions its schema in every service. That runs asynchronously, so a
          new tenant may take a few seconds to become reachable.
        </DialogContentText>

        {create.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(create.error, 'The tenant could not be created.')}
          </Alert>
        )}

        <Tabs
          value={tab}
          onChange={(_, next) => setTab(next)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ mb: 2 }}
        >
          <Tab
            label="Identity"
            icon={blocker?.tab === 0 ? <ErrorOutline fontSize="small" color="error" /> : undefined}
            iconPosition="end"
          />
          <Tab
            label="Modules"
            icon={blocker?.tab === 1 ? <ErrorOutline fontSize="small" color="error" /> : undefined}
            iconPosition="end"
          />
          <Tab label="Navigation" />
          <Tab
            label="Look"
            icon={blocker?.tab === 3 ? <ErrorOutline fontSize="small" color="error" /> : undefined}
            iconPosition="end"
          />
        </Tabs>

        <Box hidden={tab !== 0}>
          <TextField
            autoFocus
            fullWidth
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            sx={{ mb: 2 }}
            helperText='For example "Acme Builders".'
          />
          <TextField
            fullWidth
            label="Tenant key"
            value={tenantKey}
            onChange={(e) => setKeyOverride(e.target.value.trim().toLowerCase())}
            sx={{ mb: 2 }}
            error={Boolean(keyError)}
            helperText={
              keyError ||
              `Its subdomain and schema suffix — the tenant will be served at ${
                tenantKey || 'key'
              }.<your-domain>. This cannot be changed later.`
            }
          />
          <TextField
            fullWidth
            label="Custom domain"
            value={customDomain}
            onChange={(e) => setCustomDomain(e.target.value.trim().toLowerCase())}
            sx={{ mb: 2 }}
            error={!domainValid}
            placeholder="console.acme.com"
            helperText={
              !domainValid
                ? 'A hostname only — no https://, no path.'
                : 'Optional. A domain the customer owns, pointed at this platform. They keep working at their subdomain either way.'
            }
          />
          <TextField
            fullWidth
            label="Contact email"
            value={contactEmail}
            onChange={(e) => setContactEmail(e.target.value)}
            sx={{ mb: 2 }}
            error={contactEmail.trim() !== '' && !emailValid}
            helperText="Who the operator contacts about this tenant."
          />
          <TextField
            select
            fullWidth
            label="Vertical"
            value={vertical}
            onChange={(e) => {
              const next = e.target.value as Vertical;
              setVertical(next);
              // The vertical is a starting module set, so switching it replaces the selection
              // rather than merging — a leftover `bookings` on a fee-collection tenant is exactly
              // the mismatch this screen exists to prevent. The navigation overrides go with it:
              // they name items from a menu this tenant no longer has.
              setModules(new Set(VERTICAL_MODULES[next]));
              setOverrides(new Map());
              setLandingPath(null);
            }}
            helperText={chosenVertical?.description}
          >
            {VERTICALS.map((option) => (
              <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
            ))}
          </TextField>

          <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
            The vertical sets the starting modules — review them on the next tab.
          </Typography>
        </Box>

        <Box hidden={tab !== 1}>
          <ModulesStep vertical={vertical} modules={modules} onModules={setModules} />
        </Box>

        <Box hidden={tab !== 2}>
          <NavigationEditor
            modules={modules}
            overrides={overrides}
            landingPath={landingPath}
            onOverrides={setOverrides}
            onLandingPath={(path) => setLandingPath(path)}
          />
        </Box>

        <Box hidden={tab !== 3}>
          <BrandingFields
            form={branding}
            onChange={setBranding}
            name={name}
            landingLabel={landingLabelFor(landingPath)}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        {/* Clicking through to the offending field beats hunting for it. */}
        {blocker && (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ mr: 'auto', ml: 1, cursor: 'pointer' }}
            onClick={() => setTab(blocker.tab)}
          >
            {blocker.message}
          </Typography>
        )}
        <Button color="inherit" onClick={close} disabled={create.isPending}>Cancel</Button>
        {tab < 3 && (
          <Button onClick={() => setTab(tab + 1)}>Next</Button>
        )}
        <Button
          variant="contained"
          disabled={!valid || create.isPending}
          onClick={() =>
            create.mutate({
              tenantKey,
              name: name.trim(),
              contactEmail: contactEmail.trim(),
              customDomain: customDomain.trim() || undefined,
              vertical,
              modules: [...HORIZONTAL_MODULES, ...modules],
              menuOverrides: [...overrides.values()].filter((o) => !isNoopOverride(o)),
              landingPath,
              branding: toBrandingPayload(branding),
            })
          }
        >
          {create.isPending ? 'Creating…' : 'Create tenant'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

/**
 * A tenant's chosen colours, as swatches.
 *
 * Colour is the fastest way to tell tenants apart once there are more than a handful — an operator
 * recognises "the green one" long before they read a key. Shown in the list so the branding is
 * visible without opening each tenant in turn.
 */
const BrandSwatches: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const colors = ([
    ['primaryColor', 'Primary'],
    ['accentColor', 'Accent'],
    ['sidebarColor', 'Sidebar'],
  ] as const)
    .map(([key, label]) => [tenant.branding?.[key], label] as const)
    .filter(([value]) => value && HEX_COLOR.test(value));

  if (colors.length === 0) {
    return (
      <Typography variant="caption" color="text.disabled">
        Platform theme
      </Typography>
    );
  }

  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      {tenant.branding?.logoUrl && (
        <Box
          component="img"
          src={tenant.branding.logoUrl}
          alt=""
          sx={{ height: 18, maxWidth: 40, objectFit: 'contain', mr: 0.5 }}
        />
      )}
      {colors.map(([value, label]) => (
        <Tooltip key={label} title={`${label} ${value}`}>
          <Box
            sx={{
              width: 16, height: 16, borderRadius: '50%', bgcolor: value as string,
              border: '1px solid', borderColor: 'divider', flexShrink: 0,
            }}
          />
        </Tooltip>
      ))}
    </Stack>
  );
};

const TenantList: React.FC<{ tenants: Tenant[]; onOpen: (tenantKey: string) => void }> = ({
  tenants,
  onOpen,
}) => {
  const { formatDate } = useDateTime();
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<TenantStatus | 'ALL'>('ALL');

  // Name, key, subdomain and contact all identify a tenant to somebody — an operator chasing a
  // support ticket has the contact address far more often than the key.
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return tenants.filter((tenant) => {
      if (statusFilter !== 'ALL' && tenant.status !== statusFilter) return false;
      if (needle === '') return true;
      return [tenant.name, tenant.tenantKey, tenant.subdomain, tenant.contactEmail]
        .some((field) => (field || '').toLowerCase().includes(needle));
    });
  }, [tenants, query, statusFilter]);

  const { sorted, sort, onSort } = useTableSort(filtered, {
    name: (t) => t.name,
    tenantKey: (t) => t.tenantKey,
    status: (t) => t.status,
    vertical: (t) => t.vertical,
    moduleCount: (t) => t.modules.length,
    createdAt: (t) => t.createdAt,
  }, { key: 'name' });

  if (tenants.length === 0) {
    return (
      <Card>
        <CardContent>
          <Typography color="text.secondary">
            No tenants yet. Create the first one to start serving a workspace.
          </Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ p: 2 }}>
        <TextField
          size="small"
          placeholder="Search name, key, subdomain or contact"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          sx={{ flex: 1 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start"><Search fontSize="small" /></InputAdornment>
            ),
          }}
        />
        <TextField
          select
          size="small"
          label="Status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as TenantStatus | 'ALL')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="ALL">All statuses</MenuItem>
          {TENANT_STATUSES.map((status) => (
            <MenuItem key={status} value={status}>{status}</MenuItem>
          ))}
        </TextField>
      </Stack>

      {sorted.length === 0 && (
        <Box sx={{ p: 3 }}>
          <Typography color="text.secondary">
            No tenant matches that search.
          </Typography>
        </Box>
      )}

      <Table>
        <TableHead>
          <TableRow>
            <SortableTableCell columnKey="name" sort={sort} onSort={onSort}>Tenant</SortableTableCell>
            <TableCell>Brand</TableCell>
            <SortableTableCell columnKey="status" sort={sort} onSort={onSort}>Status</SortableTableCell>
            <SortableTableCell columnKey="vertical" sort={sort} onSort={onSort}>Vertical</SortableTableCell>
            <SortableTableCell columnKey="moduleCount" sort={sort} onSort={onSort} align="right">Modules</SortableTableCell>
            <TableCell>Contact</TableCell>
            <SortableTableCell columnKey="createdAt" sort={sort} onSort={onSort}>Created</SortableTableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.map((tenant) => (
            <TableRow
              key={tenant.tenantKey}
              hover
              sx={(theme) => {
                const accent = rowAccent(tenant);
                if (!accent) return {};
                return {
                  // The whole row carries the tenant's colour: a full-strength left edge, and the
                  // colour itself washed across the background. The wash is kept very low —
                  // stronger than this and the row's own text stops meeting contrast against a
                  // colour the operator, not the designer, picked.
                  borderLeft: '3px solid',
                  borderLeftColor: accent,
                  backgroundColor: alpha(accent, theme.palette.mode === 'dark' ? 0.14 : 0.07),
                  '&:hover': {
                    backgroundColor: `${alpha(
                      accent,
                      theme.palette.mode === 'dark' ? 0.24 : 0.14
                    )} !important`,
                  },
                };
              }}
            >
              <TableCell>
                <Stack direction="row" spacing={1} alignItems="center">
                  {/* A filled dot rather than coloured text: the name has to stay readable, and a
                      tenant-chosen colour cannot be trusted to contrast with the page. */}
                  {rowAccent(tenant) && (
                    <Box
                      sx={{
                        width: 10, height: 10, borderRadius: '50%', flexShrink: 0,
                        bgcolor: rowAccent(tenant) as string,
                      }}
                    />
                  )}
                  <Box>
                    <Typography variant="body2" fontWeight={600}>{tenant.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {tenant.subdomain || tenant.tenantKey}
                    </Typography>
                  </Box>
                </Stack>
              </TableCell>
              <TableCell><BrandSwatches tenant={tenant} /></TableCell>
              <TableCell>
                <Tooltip title={TENANT_STATUS_HELP[tenant.status]}>
                  <Chip size="small" label={tenant.status} color={STATUS_COLOR[tenant.status]} />
                </Tooltip>
              </TableCell>
              <TableCell>
                <Typography variant="body2">
                  {VERTICALS.find((v) => v.value === tenant.vertical)?.label || tenant.vertical}
                </Typography>
              </TableCell>
              <TableCell align="right">{tenant.modules.length}</TableCell>
              <TableCell>
                <Typography variant="caption" color="text.secondary">
                  {tenant.contactEmail || '—'}
                </Typography>
              </TableCell>
              <TableCell>
                <Typography variant="caption" color="text.secondary">
                  {formatDate(tenant.createdAt)}
                </Typography>
              </TableCell>
              <TableCell align="right">
                <Button
                  size="small"
                  onClick={() => onOpen(tenant.tenantKey)}
                  sx={{ color: rowAccent(tenant) || undefined }}
                >
                  Configure
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Card>
  );
};

const StatusEditor: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<TenantStatus | null>(null);

  const change = useMutation({
    mutationFn: (status: TenantStatus) => changeTenantStatus(tenant.tenantKey, status),
    onSuccess: () => {
      setPending(null);
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
    },
  });

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>Status</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {TENANT_STATUS_HELP[tenant.status]}
        </Typography>

        {change.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(change.error, 'The status could not be changed.')}
          </Alert>
        )}

        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {OPERATOR_SETTABLE_STATUSES.map((status) => (
            <Tooltip key={status} title={TENANT_STATUS_HELP[status]}>
              <span>
                <Button
                  size="small"
                  variant={tenant.status === status ? 'contained' : 'outlined'}
                  color={status === 'SUSPENDED' || status === 'ARCHIVED' ? 'error' : 'primary'}
                  disabled={tenant.status === status || change.isPending}
                  onClick={() => setPending(status)}
                >
                  {status}
                </Button>
              </span>
            </Tooltip>
          ))}
        </Stack>
      </CardContent>

      {/* Suspending cuts every request to a live tenant at the gateway, so it gets a confirm
          rather than taking effect on the click that was aimed at the button beside it. */}
      <Dialog open={pending !== null} onClose={() => setPending(null)}>
        <DialogTitle>Set {tenant.name} to {pending}?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {pending && TENANT_STATUS_HELP[pending]}
            {pending === 'SUSPENDED' &&
              ' Everyone signed in to this tenant stops being served immediately.'}
            {pending === 'ARCHIVED' &&
              ' Its data is retained, but nothing routes here any more.'}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setPending(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={change.isPending}
            onClick={() => pending && change.mutate(pending)}
          >
            {change.isPending ? 'Saving…' : 'Confirm'}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

/**
 * Modules and menu for a live tenant, edited together.
 *
 * They are one decision presented as one screen because they are one decision: the module set
 * decides the menu, and editing them apart is what let a tenant's navigation drift from what its
 * gateway actually serves.
 */
/**
 * A tenant's identity, editable.
 *
 * Nothing here was editable before: the operator API could create a tenant and change its modules,
 * menu and branding, but a typo in the name was permanent and `custom_domain` was a column no code
 * path could set. The tenant key stays read-only — it names this tenant's schema in every service
 * database, so changing it is a migration rather than an edit.
 */
const IdentityEditor: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<UpdateTenantCommand>({
    name: tenant.name,
    subdomain: tenant.subdomain || tenant.tenantKey,
    contactEmail: tenant.contactEmail || '',
    customDomain: tenant.customDomain || '',
    plan: tenant.plan || '',
    vertical: tenant.vertical,
  });

  // Re-seeded when the server's copy changes, so a save made elsewhere is not overwritten by a
  // stale form the next time this operator hits Save.
  useEffect(() => {
    setForm({
      name: tenant.name,
      subdomain: tenant.subdomain || tenant.tenantKey,
      contactEmail: tenant.contactEmail || '',
      customDomain: tenant.customDomain || '',
      plan: tenant.plan || '',
      vertical: tenant.vertical,
    });
  }, [tenant]);

  const save = useMutation({
    mutationFn: () =>
      updateTenant(tenant.tenantKey, {
        ...form,
        customDomain: form.customDomain?.trim() || '',
        plan: form.plan?.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenants', tenant.tenantKey] });
    },
  });

  const set = (patch: Partial<UpdateTenantCommand>) => setForm({ ...form, ...patch });

  const emailValid = /^\S+@\S+\.\S+$/.test((form.contactEmail || '').trim());
  const domainValid =
    (form.customDomain || '').trim() === '' || HOSTNAME.test((form.customDomain || '').trim());
  const subdomainValid = /^[a-z0-9][a-z0-9-]{0,62}$/.test(form.subdomain || '');
  const valid =
    form.name.trim() !== '' && emailValid && domainValid && subdomainValid;

  const hostChanged =
    (form.subdomain || '') !== (tenant.subdomain || '') ||
    (form.customDomain || '').trim() !== (tenant.customDomain || '');
  const dirty =
    hostChanged ||
    form.name !== tenant.name ||
    (form.contactEmail || '') !== (tenant.contactEmail || '') ||
    (form.plan || '') !== (tenant.plan || '') ||
    form.vertical !== tenant.vertical;

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>Identity</Typography>

        {save.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(save.error, 'The tenant could not be updated.')}
          </Alert>
        )}
        {save.isSuccess && !dirty && (
          <Alert severity="success" sx={{ mb: 2 }}>Saved.</Alert>
        )}

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
          <TextField
            fullWidth
            size="small"
            label="Name"
            value={form.name}
            onChange={(e) => set({ name: e.target.value })}
          />
          <TextField
            fullWidth
            size="small"
            label="Tenant key"
            value={tenant.tenantKey}
            disabled
            helperText="Names this tenant's schema in every service — permanent."
          />
          <TextField
            fullWidth
            size="small"
            label="Subdomain"
            value={form.subdomain}
            onChange={(e) => set({ subdomain: e.target.value.trim().toLowerCase() })}
            error={!subdomainValid}
            helperText={
              subdomainValid
                ? 'Where the tenant is served, unless a custom domain is set.'
                : 'Lowercase letters, digits and hyphens.'
            }
          />
          <TextField
            fullWidth
            size="small"
            label="Custom domain"
            value={form.customDomain}
            onChange={(e) => set({ customDomain: e.target.value.trim().toLowerCase() })}
            error={!domainValid}
            placeholder="console.acme.com"
            helperText={
              domainValid
                ? 'A domain the customer owns. Clear it to serve them at the subdomain again.'
                : 'A hostname only — no https://, no path.'
            }
          />
          <TextField
            fullWidth
            size="small"
            label="Contact email"
            value={form.contactEmail}
            onChange={(e) => set({ contactEmail: e.target.value })}
            error={(form.contactEmail || '').trim() !== '' && !emailValid}
            helperText="Who the operator contacts about this tenant."
          />
          <TextField
            fullWidth
            size="small"
            label="Plan"
            value={form.plan}
            onChange={(e) => set({ plan: e.target.value })}
            helperText="A label for billing. It does not gate anything."
          />
          <TextField
            select
            fullWidth
            size="small"
            label="Vertical"
            value={form.vertical}
            onChange={(e) => set({ vertical: e.target.value as Vertical })}
            helperText="Relabels the tenant only — it does not change their modules."
          >
            {VERTICALS.map((option) => (
              <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
            ))}
          </TextField>
        </Box>

        {hostChanged && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            Changing where a tenant is served takes up to a minute to reach the gateway, and anyone
            using the old address stops resolving once it does.
          </Alert>
        )}

        <Button
          variant="contained"
          sx={{ mt: 2 }}
          disabled={!dirty || !valid || save.isPending}
          onClick={() => save.mutate()}
        >
          {save.isPending ? 'Saving…' : 'Save identity'}
        </Button>
      </CardContent>
    </Card>
  );
};

/**
 * A live tenant's module set.
 *
 * Modules only — the navigation moved to its own card. They were one screen while hiding a tab and
 * removing a module looked like the same kind of decision; they are not. Removing a module closes the
 * API route at the gateway, so it 404s a user mid-session, and that deserves its own confirmation
 * rather than sharing one with "renamed a tab".
 */
const ModuleEditor: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const [modules, setModules] = useState<Set<string>>(new Set(tenant.modules));
  const [confirming, setConfirming] = useState(false);

  // Re-seeded whenever the server's copy changes, so a save made elsewhere is not silently
  // overwritten by a stale local set the next time this operator hits Save.
  useEffect(() => setModules(new Set(tenant.modules)), [tenant.modules]);

  const save = useMutation({
    mutationFn: () => setTenantModules(tenant.tenantKey, [...HORIZONTAL_MODULES, ...modules]),
    onSuccess: () => {
      setConfirming(false);
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenants', tenant.tenantKey] });
    },
  });

  /** Modules already on that this vertical does not list, so they can still be switched off. */
  const extraModules = useMemo(
    () =>
      tenant.modules.filter(
        (key) =>
          !(VERTICAL_MODULES[tenant.vertical] || []).includes(key) &&
          !HORIZONTAL_MODULES.includes(key as typeof HORIZONTAL_MODULES[number])
      ),
    [tenant.modules, tenant.vertical]
  );

  const removed = tenant.modules.filter(
    (key) =>
      !modules.has(key) &&
      !HORIZONTAL_MODULES.includes(key as typeof HORIZONTAL_MODULES[number])
  );
  const added = [...modules].filter((key) => !tenant.modules.includes(key));
  const dirty = removed.length > 0 || added.length > 0;

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>Modules</Typography>

        {save.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(save.error, 'The modules could not be saved.')}
          </Alert>
        )}

        <ModulesStep
          vertical={tenant.vertical}
          modules={modules}
          onModules={setModules}
          extraModules={extraModules}
        />

        <Button
          variant="contained"
          sx={{ mt: 3 }}
          disabled={!dirty || save.isPending}
          onClick={() => setConfirming(true)}
        >
          {save.isPending ? 'Saving…' : 'Save modules'}
        </Button>
      </CardContent>

      {/* Removing a module starts 404ing that tenant's users mid-session, so the removals are
          named back before it happens rather than left to be inferred from the checkboxes. */}
      <Dialog open={confirming} onClose={() => setConfirming(false)}>
        <DialogTitle>Change modules for {tenant.name}?</DialogTitle>
        <DialogContent>
          <DialogContentText component="div">
            {removed.length > 0 && (
              <p>
                Removing <strong>{removed.map(moduleLabel).join(', ')}</strong> takes
                {removed.length === 1 ? ' it' : ' them'} away immediately — anyone using
                {removed.length === 1 ? ' that area' : ' those areas'} starts getting 404s, and the
                matching menu items disappear.
              </p>
            )}
            {added.length > 0 && <p>Adding {added.map(moduleLabel).join(', ')}.</p>}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setConfirming(false)}>Cancel</Button>
          <Button
            variant="contained"
            color={removed.length > 0 ? 'error' : 'primary'}
            disabled={save.isPending}
            onClick={() => save.mutate()}
          >
            {save.isPending ? 'Saving…' : 'Confirm'}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

/**
 * A live tenant's navigation: order, labels, what is hidden, and where they land.
 *
 * Saved in one call with the landing page, because the server refuses a landing page that is also
 * being hidden and there is no useful intermediate state between the two.
 */
const NavigationCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();

  const seed = useMemo(
    () =>
      new Map((tenant.menuOverrides || []).map((override) => [override.itemKey, override])),
    [tenant.menuOverrides]
  );

  const [overrides, setOverrides] = useState<Map<string, TenantMenuOverride>>(seed);
  const [landingPath, setLandingPath] = useState<string | null>(tenant.landingPath);
  // Null on a freshly loaded tenant: only the stored path comes back, and the key is recovered when
  // the operator picks a landing page in this session. The server skips its check without one.
  const [landingItemKey, setLandingItemKey] = useState<string | null>(null);

  useEffect(() => setOverrides(seed), [seed]);
  useEffect(() => {
    setLandingPath(tenant.landingPath);
    setLandingItemKey(null);
  }, [tenant.landingPath]);

  const save = useMutation({
    mutationFn: () =>
      setTenantNavigation(
        tenant.tenantKey,
        [...overrides.values()],
        landingPath,
        landingItemKey
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenants', tenant.tenantKey] });
    },
  });

  /** Compared as sorted JSON so field order in the payload does not read as a change. */
  const signature = (map: Map<string, TenantMenuOverride>) =>
    JSON.stringify(
      [...map.values()]
        .filter((o) => !isNoopOverride(o))
        .map((o) => [o.itemKey, o.visible !== false, o.labelOverride || '', o.sortOrder ?? -1])
        .sort()
    );

  const dirty =
    signature(overrides) !== signature(seed) || landingPath !== tenant.landingPath;

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>Navigation</Typography>

        {save.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(save.error, 'The navigation could not be saved.')}
          </Alert>
        )}

        <NavigationEditor
          modules={new Set(tenant.modules)}
          overrides={overrides}
          landingPath={landingPath}
          onOverrides={setOverrides}
          onLandingPath={(path, itemKey) => {
            setLandingPath(path);
            setLandingItemKey(itemKey);
          }}
        />

        <Button
          variant="contained"
          sx={{ mt: 3 }}
          disabled={!dirty || save.isPending}
          onClick={() => save.mutate()}
        >
          {save.isPending ? 'Saving…' : 'Save navigation'}
        </Button>
      </CardContent>
    </Card>
  );
};

/**
 * Editing a live tenant's branding.
 *
 * The warning is the point of this dialog. Seeding a tenant that has never touched its theme is
 * harmless; replacing colours a tenant chose for themselves is not, and the two are
 * indistinguishable from the operator's side without asking admin-service. So we ask, and say
 * plainly which one this is before the operator commits.
 */
const EditBrandingDialog: React.FC<{
  tenant: Tenant;
  open: boolean;
  onClose: () => void;
}> = ({ tenant, open, onClose }) => {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<BrandingForm>(toBrandingForm(tenant.branding));

  // Re-seeded each time it opens, so a cancelled edit does not persist into the next one.
  useEffect(() => {
    if (open) setForm(toBrandingForm(tenant.branding));
  }, [open, tenant.branding]);

  const themeStatus = useQuery({
    queryKey: ['tenants', tenant.tenantKey, 'theme-status'],
    queryFn: () => fetchTenantThemeStatus(tenant.tenantKey),
    enabled: open,
    retry: false,
  });

  const save = useMutation({
    mutationFn: (branding: TenantBranding) => setTenantBranding(tenant.tenantKey, branding),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      onClose();
    },
  });

  const close = () => {
    if (save.isPending) return;
    save.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="md">
      <DialogTitle>Branding for {tenant.name}</DialogTitle>
      <DialogContent>
        {themeStatus.data?.customised && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            This tenant has customised its own theme. Saving replaces what they chose.
          </Alert>
        )}
        {themeStatus.data && !themeStatus.data.customised && (
          <Alert severity="info" sx={{ mb: 2 }}>
            This tenant is still on the default theme — nothing of theirs is overwritten.
          </Alert>
        )}
        {save.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(save.error, 'The branding could not be saved.')}
          </Alert>
        )}

        <BrandingFields form={form} onChange={setForm} name={tenant.name} />
      </DialogContent>
      <DialogActions>
        {!brandingValid(form) && (
          <Typography variant="caption" color="text.secondary" sx={{ mr: 'auto', ml: 1 }}>
            Check the branding values.
          </Typography>
        )}
        <Button color="inherit" onClick={close} disabled={save.isPending}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!brandingValid(form) || save.isPending}
          onClick={() => save.mutate(toBrandingPayload(form) || {})}
        >
          {save.isPending ? 'Saving…' : 'Save branding'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

/**
 * What branding the tenant currently has, and the way in to changing it.
 *
 * Editing re-pushes to the tenant's own theme, which is why it goes through a dialog that first
 * asks admin-service whether the tenant has customised anything — see EditBrandingDialog.
 */
const BrandingSummary: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const [editing, setEditing] = useState(false);
  const branding = tenant.branding;
  const chosen = branding
    ? (Object.entries(branding) as Array<[string, string | null]>)
        .filter(([, value]) => value !== null && value !== '')
    : [];

  if (chosen.length === 0) {
    return (
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
            <Box>
              <Typography variant="h6" gutterBottom>Branding</Typography>
              <Typography variant="body2" color="text.secondary">
                Onboarded with no branding — this tenant started on the platform theme.
              </Typography>
            </Box>
            <Button size="small" startIcon={<Edit />} onClick={() => setEditing(true)}>
              Set branding
            </Button>
          </Stack>
        </CardContent>
        <EditBrandingDialog tenant={tenant} open={editing} onClose={() => setEditing(false)} />
      </Card>
    );
  }

  const colorOf = (key: string) =>
    key.endsWith('Color') ? (branding as Record<string, string>)[key] : null;

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="h6" gutterBottom>Branding</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              What this tenant's console is set up to look like.
            </Typography>
          </Box>
          <Button size="small" startIcon={<Edit />} onClick={() => setEditing(true)}>
            Edit
          </Button>
        </Stack>

        {branding?.logoUrl && (
          <Box
            component="img"
            src={branding.logoUrl}
            alt={`${tenant.name} logo`}
            sx={{
              maxHeight: 48, maxWidth: '100%', mb: 2, display: 'block',
              border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 0.5,
            }}
          />
        )}

        <Stack spacing={1}>
          {chosen
            .filter(([key]) => key !== 'logoUrl')
            .map(([key, value]) => (
              <Stack key={key} direction="row" spacing={1} alignItems="center">
                {colorOf(key) && (
                  <Box
                    sx={{
                      width: 20, height: 20, borderRadius: 0.5, bgcolor: value as string,
                      border: '1px solid', borderColor: 'divider', flexShrink: 0,
                    }}
                  />
                )}
                <Typography variant="body2" sx={{ minWidth: 120 }} color="text.secondary">
                  {key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())}
                </Typography>
                <Typography variant="body2" fontWeight={500}>{value}</Typography>
              </Stack>
            ))}
        </Stack>
      </CardContent>
      <EditBrandingDialog tenant={tenant} open={editing} onClose={() => setEditing(false)} />
    </Card>
  );
};

/**
 * One tenant, in the same four sections the create wizard uses.
 *
 * The row from the list is passed in so the header paints immediately, but the sections read from a
 * fresh single-tenant fetch: the list endpoint deliberately does not query menu overrides — they are
 * a second query per tenant and the list renders none of them — so a navigation editor built from a
 * list row would show every tenant as having no overrides at all.
 */
const TenantDetail: React.FC<{ tenant: Tenant; onBack: () => void }> = ({
  tenant: fromList,
  onBack,
}) => {
  const { formatDateTime } = useDateTime();
  const { data, isError, error } = useQuery({
    queryKey: ['tenants', fromList.tenantKey],
    queryFn: () => fetchTenant(fromList.tenantKey),
    retry: false,
  });

  const tenant = data || fromList;
  const accent = rowAccent(tenant);

  return (
    <Box>
      <Button startIcon={<ArrowBack />} onClick={onBack} sx={{ mb: 2 }}>
        All tenants
      </Button>
      <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 0.5 }}>
        {accent && (
          <Box
            sx={{ width: 14, height: 14, borderRadius: '50%', flexShrink: 0, bgcolor: accent }}
          />
        )}
        <Typography variant="h5">{tenant.name}</Typography>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {tenant.tenantKey}
        {tenant.subdomain && tenant.subdomain !== tenant.tenantKey && ` · ${tenant.subdomain}`}
        {tenant.customDomain && ` · ${tenant.customDomain}`}
        {` · ${tenant.contactEmail || 'no contact'}`}
        {tenant.createdAt && ` · created ${formatDateTime(tenant.createdAt)}`}
      </Typography>

      {/* The header above came from the list, so this is a degraded view rather than a dead end. */}
      {isError && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          {apiErrorMessage(error, 'Could not reload this tenant, so its navigation may be out of date.')}
        </Alert>
      )}

      <StatusEditor tenant={tenant} />
      <IdentityEditor tenant={tenant} />
      <ModuleEditor tenant={tenant} />
      {/* After modules: the navigation can only reshape what the module set left behind. */}
      <NavigationCard tenant={tenant} />
      <BrandingSummary tenant={tenant} />
    </Box>
  );
};

const TenantManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const [openKey, setOpenKey] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const { data, isLoading, isError, error } = useQuery<Tenant[]>({
    queryKey: ['tenants'],
    queryFn: fetchTenants,
    retry: false,
  });

  const status = (error as { response?: { status?: number } })?.response?.status;
  const tenants = data || [];
  const open = openKey ? tenants.find((t) => t.tenantKey === openKey) : undefined;

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }

  // The operator-only rule lives in tenant-service; saying so beats an empty table that looks
  // like the platform has no tenants.
  if (isError && status === 403) {
    return (
      <Alert severity="info">
        Only a Super Admin of the <strong>platform</strong> tenant can manage tenants. A tenant's
        own Super Admin cannot create or suspend another tenant.
      </Alert>
    );
  }
  if (isError) {
    return <Alert severity="error">{apiErrorMessage(error, 'Could not load the tenant list.')}</Alert>;
  }

  if (open) {
    return <TenantDetail tenant={open} onBack={() => setOpenKey(null)} />;
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h5">Tenants</Typography>
          <Typography variant="body2" color="text.secondary">
            Every workspace served by this platform.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={() => setCreating(true)}>
          New tenant
        </Button>
      </Stack>

      <TenantList tenants={tenants} onOpen={setOpenKey} />

      <NewTenantDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(tenant) => {
          setCreating(false);
          queryClient.invalidateQueries({ queryKey: ['tenants'] });
          setOpenKey(tenant.tenantKey);
        }}
      />
    </Box>
  );
};

export default TenantManagement;
