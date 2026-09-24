import { createTheme, Theme } from '@mui/material/styles';
import type { ResolvedTheme } from './services/uiConfigApi';
import { SemanticTokens, cssVariables, resolveTokens } from './experience/tokens';
import { stylePack } from './experience/stylePacks';

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

/**
 * The tenant's configuration → an MUI theme, through the experience engines (architecture 05):
 * the token pipeline turns a few colour seeds into ramps and per-mode semantic tokens, and the
 * style pack decides what surfaces, shapes and buttons feel like. A null field means "inherit the
 * built-in default", so every default lives here rather than as a server-side guess.
 *
 * The resolved tokens and pack ride along on the theme as `theme.experience` for anything that
 * needs them directly (the home page's blocks, charts), and as CSS custom properties.
 */
export const buildTheme = (config?: ResolvedTheme | null): Theme => {
  const mode = resolveMode(config?.mode);
  const pack = stylePack(config?.uiStyle);
  const tokens = resolveTokens({
    mode,
    primary: config?.primaryColor || DEFAULT_PRIMARY,
    accent: config?.accentColor || DEFAULT_ACCENT,
    surface: config?.surfaceColor,
  });
  if (tokens.adjusted.length) {
    console.warn('[experience] contrast fallback applied:', tokens.adjusted.join('; '));
  }
  const radii = pack.radius(config?.borderRadius ?? DEFAULT_RADIUS);
  const fontFamily = config?.fontFamily || DEFAULT_FONT;
  const headingFont = pack.headingFont ?? fontFamily;
  const spacing = (DENSITY_SPACING[config?.density || 'comfortable'] ?? 8) + (pack.spacingBoost ?? 0);

  // The tenant's button style applies to packs that leave buttons to it; the others define their
  // own button (a brutalist button is a hard-edged block whatever the tenant picked).
  const buttonStyle = config?.buttonStyle || 'gradient';
  const gradient = `linear-gradient(135deg, ${tokens.primary} 0%, ${tokens.accent} 100%)`;
  const contained = pack.button
    ? { ...pack.button(tokens), '&:hover': { ...pack.button(tokens), filter: 'brightness(1.05)' } }
    : buttonStyle === 'outlined'
      ? {
          background: 'transparent',
          color: tokens.primary,
          border: `1.5px solid ${tokens.primary}`,
          '&:hover': { background: `${tokens.primary}14`, boxShadow: 'none' },
        }
      : { background: buttonStyle === 'solid' ? tokens.primary : gradient, color: tokens.onPrimary, boxShadow: 'none' };

  return createTheme(
    {
      spacing,
      palette: {
        mode,
        primary: { main: tokens.primary, contrastText: tokens.onPrimary },
        secondary: { main: tokens.accent, contrastText: tokens.onAccent },
        success: { main: '#10b981', light: '#34d399', dark: '#059669' },
        warning: { main: '#f59e0b', light: '#fbbf24', dark: '#d97706' },
        error: { main: '#ef4444', light: '#f87171', dark: '#dc2626' },
        background: { paper: tokens.surface, default: tokens.background },
        text: { primary: tokens.text, secondary: tokens.textMuted },
        divider: tokens.border,
      },
      typography: {
        fontFamily,
        h1: { fontFamily: headingFont, fontWeight: 800, fontSize: '2.5rem', lineHeight: 1.2 },
        h2: { fontFamily: headingFont, fontWeight: 700, fontSize: '2rem', lineHeight: 1.3 },
        h3: { fontFamily: headingFont, fontWeight: 600, fontSize: '1.5rem', lineHeight: 1.4 },
        h4: { fontFamily: headingFont, fontWeight: 600, fontSize: '1.25rem' },
        h5: { fontFamily, fontWeight: 600, fontSize: '1.1rem' },
        h6: { fontFamily, fontWeight: 500, fontSize: '1rem' },
        button: { textTransform: 'none', fontWeight: 600, ...(pack.buttonText ?? {}) },
      },
      shape: { borderRadius: radii.control + 4 },
      components: {
        MuiCssBaseline: {
          styleOverrides: {
            ':root': cssVariables(tokens),
            body: { background: pack.background(tokens), backgroundAttachment: 'fixed' },
          },
        },
        MuiButton: {
          styleOverrides: {
            root: { borderRadius: radii.control, padding: '10px 24px', fontSize: '0.95rem', boxShadow: 'none' },
            contained,
          },
        },
        MuiCard: {
          styleOverrides: {
            root: { borderRadius: radii.card, ...pack.card(tokens), '&:hover': pack.cardHover(tokens) },
          },
        },
        MuiTextField: {
          styleOverrides: {
            root: {
              '& .MuiOutlinedInput-root': {
                borderRadius: radii.control,
                '&:hover fieldset': { borderColor: tokens.primary },
              },
            },
          },
        },
      },
    },
    { experience: { tokens, pack: pack.key, siteLayout: config?.siteLayout || 'marketplace' } }
  );
};

/** The engines' output for a theme built by {@link buildTheme}. */
export const experienceOf = (t: Theme): { tokens: SemanticTokens; pack: string; siteLayout: string } =>
  (t as unknown as { experience: { tokens: SemanticTokens; pack: string; siteLayout: string } }).experience;

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
