import { createTheme, Theme } from '@mui/material/styles';
import type { ResolvedTheme } from './services/uiConfigApi';

/**
 * The shipped design system, and the function that overlays the admin-editable UI config on top
 * of it.
 *
 * A null field in a {@link ResolvedTheme} means "inherit the built-in default" — never "no
 * value" — so every default lives here rather than being restated as a server-side guess. That
 * is what lets Super Admin change one accent colour without having to define a whole palette.
 */

const DEFAULT_PRIMARY = '#667eea';
const DEFAULT_ACCENT = '#764ba2';
const DEFAULT_FONT = "'Inter', 'Poppins', 'Roboto', sans-serif";
const DEFAULT_RADIUS = 12;
const DEFAULT_SIDEBAR = '#1e293b';

/**
 * How much lift cards and buttons get. 'flat' is not "smaller shadows" but none at all — the
 * point of the style is that depth is expressed with borders instead, so a half-flat card would
 * read as a mistake rather than a choice.
 */
const SHADOWS: Record<string, { card: string; cardHover: string; button: string }> = {
  flat: { card: 'none', cardHover: 'none', button: 'none' },
  elevated: {
    card: '0 6px 16px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08)',
    cardHover: '0 18px 40px rgba(0,0,0,0.18)',
    button: '0 4px 12px rgba(0,0,0,0.16)',
  },
};

/** Spacing unit per density — the one knob that changes how much fits on a screen. */
const DENSITY_SPACING: Record<string, number> = {
  compact: 6,
  comfortable: 8,
  spacious: 10,
};

/**
 * 'light'/'dark' is what the admin or member explicitly chose; only an explicit 'system' follows
 * the OS. Unset means the shipped CivEngMarket light theme — following the OS by default flipped
 * the whole app to dark for anyone on a dark desktop, which is a theme change nobody asked for.
 */
export const resolveMode = (mode: string | null | undefined): 'light' | 'dark' => {
  if (mode === 'light' || mode === 'dark') return mode;
  if (mode === 'system') {
    return typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }
  return 'light';
};

export const buildTheme = (config?: ResolvedTheme | null): Theme => {
  const mode = resolveMode(config?.mode);
  const primary = config?.primaryColor || DEFAULT_PRIMARY;
  const accent = config?.accentColor || DEFAULT_ACCENT;
  const radius = config?.borderRadius ?? DEFAULT_RADIUS;
  const fontFamily = config?.fontFamily || DEFAULT_FONT;
  const spacing = DENSITY_SPACING[config?.density || 'comfortable'] ?? 8;
  const dark = mode === 'dark';

  // The surface colour drives the paper layer; the page behind it is derived rather than
  // configured, so an admin picking one colour cannot leave cards invisible against the page.
  //
  // One stored colour has to serve both modes, and a colour chosen while looking at the light
  // theme is almost always a pale one. Applied literally in dark mode it painted white cards on a
  // dark page with pale text on them — unreadable, and looking like the theme had failed to
  // change. So it is honoured only when it actually suits the mode being rendered; otherwise the
  // mode's own default paper is used and the admin's choice waits for the mode it was picked for.
  const surfaceSuitsMode =
    config?.surfaceColor != null && isLight(config.surfaceColor) === !dark;
  const paper = surfaceSuitsMode
    ? (config!.surfaceColor as string)
    : dark
      ? '#1e293b'
      : '#ffffff';
  const defaultBg = surfaceSuitsMode
    ? undefined // let MUI derive a page colour that contrasts with the chosen surface
    : dark
      ? '#0f172a'
      : '#f8fafc';

  const gradient = `linear-gradient(135deg, ${primary} 0%, ${accent} 100%)`;

  const uiStyle = config?.uiStyle || 'default';
  const flat = uiStyle === 'flat';
  const shadows = SHADOWS[uiStyle];
  const cardShadow = shadows
    ? shadows.card
    : dark
      ? '0 1px 3px rgba(0,0,0,0.4), 0 1px 2px rgba(0,0,0,0.3)'
      : '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)';
  const cardHoverShadow = shadows
    ? shadows.cardHover
    : dark
      ? '0 10px 25px rgba(0,0,0,0.5)'
      : '0 10px 25px rgba(0,0,0,0.1)';

  // 'outlined' can't be expressed as a fill, so it changes the button's default variant as well
  // as its background — a contained button asked to be outlined has to actually lose its fill.
  const buttonStyle = config?.buttonStyle || 'gradient';
  const containedBackground =
    buttonStyle === 'solid'
      ? primary
      : buttonStyle === 'outlined'
        ? 'transparent'
        : gradient;

  return createTheme({
    spacing,
    palette: {
      mode,
      primary: { main: primary, contrastText: '#ffffff' },
      secondary: { main: accent, contrastText: '#ffffff' },
      success: { main: '#10b981', light: '#34d399', dark: '#059669' },
      warning: { main: '#f59e0b', light: '#fbbf24', dark: '#d97706' },
      error: { main: '#ef4444', light: '#f87171', dark: '#dc2626' },
      background: {
        paper,
        ...(defaultBg ? { default: defaultBg } : {}),
      },
      text: dark
        ? { primary: '#f1f5f9', secondary: '#94a3b8' }
        : { primary: '#1e293b', secondary: '#64748b' },
    },
    typography: {
      fontFamily,
      h1: { fontFamily, fontWeight: 800, fontSize: '2.5rem', lineHeight: 1.2 },
      h2: { fontFamily, fontWeight: 700, fontSize: '2rem', lineHeight: 1.3 },
      h3: { fontFamily, fontWeight: 600, fontSize: '1.5rem', lineHeight: 1.4 },
      h4: { fontFamily, fontWeight: 600, fontSize: '1.25rem' },
      h5: { fontFamily, fontWeight: 600, fontSize: '1.1rem' },
      h6: { fontFamily, fontWeight: 500, fontSize: '1rem' },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    shape: { borderRadius: radius },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: Math.max(4, radius - 4),
            padding: '10px 24px',
            fontSize: '0.95rem',
            boxShadow: 'none',
          },
          // Follows the configured palette rather than the shipped purple, or an admin who
          // changes the primary colour ends up with buttons that ignore it.
          contained:
            buttonStyle === 'outlined'
              ? {
                  background: 'transparent',
                  color: primary,
                  border: `1.5px solid ${primary}`,
                  '&:hover': { background: `${primary}14`, boxShadow: 'none' },
                }
              : { background: containedBackground, boxShadow: shadows?.button ?? 'none' },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: radius + 4,
            boxShadow: cardShadow,
            // Flat draws its edge instead of casting one, or cards vanish into the page.
            ...(flat ? { border: '1px solid', borderColor: 'divider' } : {}),
            '&:hover': { boxShadow: cardHoverShadow },
          },
        },
      },
      MuiTextField: {
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              borderRadius: Math.max(4, radius - 4),
              '&:hover fieldset': { borderColor: primary },
            },
          },
        },
      },
    },
  });
};

/**
 * The shell's navigation colours, which are not part of the MUI palette — the sidebar is a single
 * surface owned by the layout, so it is resolved here rather than being restated in each layout.
 * Falls back to the shipped slate, which is why a theme with no sidebar colour looks unchanged.
 */
export const sidebarPalette = (config?: ResolvedTheme | null) => {
  const bg = config?.sidebarColor || DEFAULT_SIDEBAR;
  // Overlay colours are derived from the sidebar's own lightness rather than assumed dark: an
  // admin who picks a pale sidebar would otherwise get near-white labels on near-white paint.
  const light = isLight(bg);
  const veil = (alpha: number) => (light ? `rgba(0,0,0,${alpha})` : `rgba(255,255,255,${alpha})`);

  return {
    bg,
    text: light ? '#1e293b' : '#cbd5e1',
    muted: light ? '#475569' : '#94a3b8',
    icon: light ? '#64748b' : '#64748b',
    divider: veil(0.08),
    activeBg: veil(0.1),
    hoverBg: veil(0.05),
    activeHoverBg: veil(0.14),
  };
};

/**
 * Perceived lightness of a #rgb/#rrggbb colour. Anything this cannot parse (a named colour, a
 * gradient someone pasted in) is treated as dark, which matches the shipped sidebar — the
 * fallback has to be the look nobody asked to change.
 */
const isLight = (color: string): boolean => {
  const hex = color.trim().replace('#', '');
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return false;
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16));
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.6;
};

/**
 * The static fallback, used before the UI config has loaded and whenever it cannot be fetched —
 * the app must still render if admin-service is down.
 */
export const theme = buildTheme(null);
