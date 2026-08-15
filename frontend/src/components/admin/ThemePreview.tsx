import React from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Stack,
  TextField,
  ThemeProvider,
  Typography,
} from '@mui/material';
import { buildTheme, sidebarPalette } from '../../theme';
import { ResolvedTheme, ThemeUpdateCommand } from '../../services/uiConfigApi';

/**
 * The theme being edited, painted on a miniature of the shell, before it is saved.
 *
 * It renders inside its own {@link ThemeProvider} so the preview is a real MUI render of the
 * candidate theme rather than a swatch grid — the same {@link buildTheme} the app runs through,
 * so what shows here is what the workspace gets. That containment is the point: the console
 * around it keeps the admin's own theme, so a preview of an unreadable palette never leaves them
 * unable to see the form well enough to fix it.
 */

/** The editor's form is a command; buildTheme takes a resolved theme. Same fields, one shim. */
const asResolved = (form: ThemeUpdateCommand, scopeKey: string): ResolvedTheme => ({
  scopeKey,
  mode: (form.mode as ResolvedTheme['mode']) || 'system',
  primaryColor: form.primaryColor || null,
  accentColor: form.accentColor || null,
  surfaceColor: form.surfaceColor || null,
  sidebarColor: form.sidebarColor || null,
  borderRadius: form.borderRadius ?? null,
  fontFamily: form.fontFamily || null,
  brandName: form.brandName || null,
  logoUrl: form.logoUrl || null,
  uiStyle: form.uiStyle || null,
  buttonStyle: form.buttonStyle || null,
  layoutStyle: form.layoutStyle || null,
  density: form.density || null,
  version: 0,
});

interface Props {
  /** The form as it stands, including unsaved edits. */
  form: ThemeUpdateCommand;
  /**
   * What the scope inherits for the fields left blank — so the preview shows the workspace's
   * real result, not the shipped defaults, when it is inheriting from the platform theme.
   */
  inherited?: ResolvedTheme;
  scopeLabel: string;
}

const NAV_ITEMS = ['Dashboard', 'Projects', 'Bookings', 'Settings'];

const ThemePreview: React.FC<Props> = ({ form, inherited, scopeLabel }) => {
  // Blank field -> the inherited value, matching how the backend merges before it reaches a
  // client. Previewing a blank as "no colour" would show a look no user will ever see.
  const merged: ResolvedTheme = {
    ...asResolved(form, inherited?.scopeKey || 'PREVIEW'),
    primaryColor: form.primaryColor || inherited?.primaryColor || null,
    accentColor: form.accentColor || inherited?.accentColor || null,
    surfaceColor: form.surfaceColor || inherited?.surfaceColor || null,
    sidebarColor: form.sidebarColor || inherited?.sidebarColor || null,
    borderRadius: form.borderRadius ?? inherited?.borderRadius ?? null,
    fontFamily: form.fontFamily || inherited?.fontFamily || null,
    brandName: form.brandName || inherited?.brandName || null,
    logoUrl: form.logoUrl || inherited?.logoUrl || null,
    uiStyle: form.uiStyle || inherited?.uiStyle || null,
    buttonStyle: form.buttonStyle || inherited?.buttonStyle || null,
    layoutStyle: form.layoutStyle || inherited?.layoutStyle || null,
    density: form.density || inherited?.density || null,
  };

  const previewTheme = buildTheme(merged);
  const nav = sidebarPalette(merged);
  const navOnRight = merged.layoutStyle === 'sidebar-right';
  // The top bar is the one layout that is not a left/right flip, so it changes the axis rather
  // than only the direction — the nav strip then runs full width above the content.
  const navOnTop = merged.layoutStyle === 'topbar';

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" gutterBottom>Live preview</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {scopeLabel} as it will look once saved, including the fields left blank above.
        </Typography>

        <ThemeProvider theme={previewTheme}>
          <Box
            sx={{
              display: 'flex',
              flexDirection: navOnTop ? 'column' : navOnRight ? 'row-reverse' : 'row',
              minHeight: 320,
              borderRadius: 2,
              overflow: 'hidden',
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.default',
            }}
          >
            <Box
              sx={{
                bgcolor: nav.bg,
                p: 1.5,
                flexShrink: 0,
                ...(navOnTop
                  ? { display: 'flex', alignItems: 'center', gap: 1.5 }
                  : { width: 150 }),
              }}
            >
              <Stack
                direction="row"
                spacing={1}
                alignItems="center"
                sx={navOnTop ? { pr: 1 } : { px: 1, pb: 1.5 }}
              >
                {merged.logoUrl && (
                  <Box
                    component="img"
                    src={merged.logoUrl}
                    alt=""
                    sx={{ width: 22, height: 22, objectFit: 'contain' }}
                  />
                )}
                <Typography sx={{ color: nav.text, fontWeight: 700, fontSize: '0.85rem' }} noWrap>
                  {merged.brandName || 'CivEngMarket'}
                </Typography>
              </Stack>
              <Divider
                orientation={navOnTop ? 'vertical' : 'horizontal'}
                flexItem={navOnTop}
                sx={{ borderColor: nav.divider, mb: navOnTop ? 0 : 1 }}
              />
              {NAV_ITEMS.map((label, index) => (
                <Box
                  key={label}
                  sx={{
                    px: 1.5,
                    py: 0.9,
                    mb: navOnTop ? 0 : 0.5,
                    borderRadius: 1.5,
                    fontSize: '0.8rem',
                    whiteSpace: 'nowrap',
                    bgcolor: index === 0 ? nav.activeBg : 'transparent',
                    color: index === 0 ? 'primary.main' : nav.muted,
                    fontWeight: index === 0 ? 600 : 400,
                  }}
                >
                  {label}
                </Box>
              ))}
            </Box>

            <Box sx={{ flexGrow: 1, p: 2.5 }}>
              <Typography variant="h4" gutterBottom>Dashboard</Typography>
              <Card sx={{ mb: 2 }}>
                <CardContent>
                  <Typography variant="h6">Open bookings</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                    Body copy in the configured font, at the configured density.
                  </Typography>
                  <Stack direction="row" spacing={1}>
                    <Chip label="Active" color="primary" size="small" />
                    <Chip label="Pending" size="small" />
                  </Stack>
                </CardContent>
              </Card>
              <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
                <Button variant="contained" size="small">Primary action</Button>
                <Button variant="outlined" size="small">Secondary</Button>
                <TextField size="small" label="A field" sx={{ width: 160 }} />
              </Stack>
            </Box>
          </Box>
        </ThemeProvider>
      </CardContent>
    </Card>
  );
};

export default ThemePreview;
