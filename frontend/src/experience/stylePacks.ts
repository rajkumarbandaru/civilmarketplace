import type { SemanticTokens } from './tokens';
import manifest from './registry-manifest.json';

/**
 * UI style packs (architecture 05 §4): what surfaces feel like, independent of colour and layout.
 * A pack is code — adding one is a frontend release; choosing one is configuration. Every key here
 * must be listed in registry-manifest.json, which the backend validates choices against.
 */
export interface StylePack {
  key: string;
  label: string;
  /** The shape scale from the tenant's base radius. */
  radius: (base: number) => { card: number; control: number };
  card: (t: SemanticTokens) => Record<string, unknown>;
  cardHover: (t: SemanticTokens) => Record<string, unknown>;
  /** How contained buttons look; null leaves the tenant's button style in charge. */
  button: ((t: SemanticTokens) => Record<string, unknown>) | null;
  /** The page behind everything. */
  background: (t: SemanticTokens) => string;
  headingFont?: string;
  buttonText?: Record<string, unknown>;
  /** Extra spacing units for packs that breathe more. */
  spacingBoost?: number;
}

const shadow = (t: SemanticTokens, light: string, dark: string) => (t.mode === 'dark' ? dark : light);

export const STYLE_PACKS: Record<string, StylePack> = {
  default: {
    key: 'default', label: 'Default',
    radius: (b) => ({ card: b + 4, control: Math.max(4, b - 4) }),
    card: (t) => ({ boxShadow: shadow(t, '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)', '0 1px 3px rgba(0,0,0,0.4)') }),
    cardHover: (t) => ({ boxShadow: shadow(t, '0 10px 25px rgba(0,0,0,0.1)', '0 10px 25px rgba(0,0,0,0.5)') }),
    button: null,
    background: (t) => t.background,
  },
  flat: {
    key: 'flat', label: 'Flat',
    radius: (b) => ({ card: b + 4, control: Math.max(4, b - 4) }),
    // Depth is drawn with an edge instead of cast with a shadow, or cards vanish into the page.
    card: (t) => ({ boxShadow: 'none', border: `1px solid ${t.border}` }),
    cardHover: () => ({ boxShadow: 'none' }),
    button: (t) => ({ background: t.primary, color: t.onPrimary, boxShadow: 'none' }),
    background: (t) => t.background,
  },
  elevated: {
    key: 'elevated', label: 'Elevated',
    radius: (b) => ({ card: b + 4, control: Math.max(4, b - 4) }),
    card: () => ({ boxShadow: '0 6px 16px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08)' }),
    cardHover: () => ({ boxShadow: '0 18px 40px rgba(0,0,0,0.18)' }),
    button: null,
    background: (t) => t.background,
  },
  material: {
    key: 'material', label: 'Material',
    radius: (b) => ({ card: Math.min(12, Math.max(4, b)), control: Math.min(8, Math.max(4, b - 4)) }),
    card: (t) => ({ boxShadow: shadow(t, '0 2px 1px -1px rgba(0,0,0,.2),0 1px 1px 0 rgba(0,0,0,.14),0 1px 3px 0 rgba(0,0,0,.12)',
      '0 2px 1px -1px rgba(0,0,0,.5),0 1px 3px 0 rgba(0,0,0,.4)') }),
    cardHover: () => ({ boxShadow: '0 5px 5px -3px rgba(0,0,0,.2),0 8px 10px 1px rgba(0,0,0,.14),0 3px 14px 2px rgba(0,0,0,.12)' }),
    button: (t) => ({ background: t.primary, color: t.onPrimary, boxShadow: '0 3px 1px -2px rgba(0,0,0,.2),0 2px 2px 0 rgba(0,0,0,.14)' }),
    background: (t) => t.background,
    buttonText: { textTransform: 'uppercase', letterSpacing: '0.04em' },
  },
  glass: {
    key: 'glass', label: 'Glass',
    radius: (b) => ({ card: Math.max(16, b + 8), control: Math.max(10, b) }),
    // Frosted surfaces need something behind them to frost: the pack paints a brand gradient.
    // Where the user asks for less transparency the surface goes solid.
    card: (t) => ({
      background: t.mode === 'dark' ? 'rgba(15,23,42,0.55)' : 'rgba(255,255,255,0.6)',
      backdropFilter: 'blur(16px) saturate(140%)',
      border: `1px solid ${t.mode === 'dark' ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.7)'}`,
      boxShadow: '0 8px 32px rgba(31,38,135,0.15)',
      '@media (prefers-reduced-transparency: reduce)': { background: t.surface, backdropFilter: 'none' },
    }),
    cardHover: () => ({ boxShadow: '0 12px 40px rgba(31,38,135,0.25)' }),
    button: (t) => ({
      background: `linear-gradient(135deg, ${t.primary}, ${t.accent})`, color: t.onPrimary,
      boxShadow: '0 4px 20px rgba(31,38,135,0.25)',
    }),
    background: (t) => (t.mode === 'dark'
      ? `radial-gradient(1200px 600px at 10% 0%, ${t.ramps.primary[900]} 0%, transparent 60%), radial-gradient(900px 500px at 90% 10%, ${t.ramps.accent[900]} 0%, transparent 55%), ${t.background}`
      : `radial-gradient(1200px 600px at 10% 0%, ${t.ramps.primary[200]} 0%, transparent 60%), radial-gradient(900px 500px at 90% 10%, ${t.ramps.accent[200]} 0%, transparent 55%), ${t.background}`),
  },
  luxury: {
    key: 'luxury', label: 'Luxury',
    radius: () => ({ card: 2, control: 2 }),
    card: (t) => ({ boxShadow: 'none', border: `1px solid ${t.mode === 'dark' ? t.ramps.accent[800] : t.ramps.accent[200]}` }),
    cardHover: (t) => ({ borderColor: t.accent }),
    button: (t) => ({ background: t.primary, color: t.onPrimary, boxShadow: 'none', border: `1px solid ${t.accent}` }),
    background: (t) => t.background,
    headingFont: "'Playfair Display', 'Cormorant Garamond', Georgia, serif",
    buttonText: { textTransform: 'uppercase', letterSpacing: '0.18em', fontWeight: 500 },
    spacingBoost: 1,
  },
  brutalist: {
    key: 'brutalist', label: 'Neo-Brutalist',
    radius: () => ({ card: 0, control: 0 }),
    card: (t) => ({ border: `3px solid ${t.text}`, boxShadow: `6px 6px 0 ${t.text}` }),
    cardHover: (t) => ({ boxShadow: `8px 8px 0 ${t.text}`, transform: 'translate(-2px,-2px)' }),
    button: (t) => ({ background: t.accent, color: t.onAccent, border: `3px solid ${t.text}`, boxShadow: `4px 4px 0 ${t.text}` }),
    background: (t) => t.background,
    buttonText: { fontWeight: 800 },
  },
};

/** The pack for a key; an unknown key (a newer config on an older build) falls back, with a warning. */
export const stylePack = (key: string | null | undefined): StylePack => {
  const pack = key ? STYLE_PACKS[key] : undefined;
  if (key && !pack) console.warn(`[experience] unknown style pack '${key}', using default`);
  return pack ?? STYLE_PACKS.default;
};

/** Registry and manifest must agree: a key without a renderer, or a renderer nobody can select, is a bug. */
export const registryMismatch = (): string[] => {
  const code = Object.keys(STYLE_PACKS).sort();
  const listed = [...manifest.stylePacks].sort();
  return [...code.filter((k) => !listed.includes(k)), ...listed.filter((k) => !code.includes(k))];
};
