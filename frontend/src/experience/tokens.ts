import { Ramp, RAMP_STEPS, RampStep, contrast, generateRamp, hexToOklch, oklchToHex, onColor, readableStep } from './color';

/**
 * The token pipeline (architecture 05 §2.1): a tenant's few semantic seeds → generated ramps →
 * semantic tokens per colour mode → CSS custom properties. Components read tokens, never literals.
 */

export interface SemanticTokens {
  mode: 'light' | 'dark';
  primary: string;
  onPrimary: string;
  accent: string;
  onAccent: string;
  background: string;
  surface: string;
  text: string;
  textMuted: string;
  border: string;
  ramps: { primary: Ramp; accent: Ramp; neutral: Ramp };
  /** Pairs the runtime fallback had to adjust (logged once; validation should have caught them). */
  adjusted: string[];
}

export interface TokenInput {
  mode: 'light' | 'dark';
  primary: string;
  accent: string;
  surface?: string | null;
}

/** A low-chroma ramp in the brand's hue: backgrounds and borders that sit with the palette. */
const neutralRamp = (primary: string): Ramp => {
  const h = hexToOklch(primary)?.h ?? 260;
  const ramp = {} as Ramp;
  const L: Record<RampStep, number> = {
    50: 0.985, 100: 0.967, 200: 0.925, 300: 0.87, 400: 0.71, 500: 0.55, 600: 0.45, 700: 0.37, 800: 0.28, 900: 0.21, 950: 0.15,
  };
  for (const s of RAMP_STEPS) ramp[s] = oklchToHex({ l: L[s], c: 0.012 + (s >= 700 ? 0.01 : 0), h });
  return ramp;
};

const anchorOf = (ramp: Ramp, seed: string): RampStep =>
  (RAMP_STEPS.find((s) => ramp[s].toLowerCase() === seed.toLowerCase()) ?? 500);

export const resolveTokens = ({ mode, primary, accent, surface }: TokenInput): SemanticTokens => {
  const pr = generateRamp(primary);
  const ac = generateRamp(accent);
  const neutral = neutralRamp(primary);
  const dark = mode === 'dark';
  const adjusted: string[] = [];

  const background = dark ? neutral[950] : neutral[50];
  const surfaceTone = surface && (contrast(surface, dark ? '#000000' : '#ffffff') < 2) ? surface : dark ? neutral[900] : '#ffffff';
  const text = dark ? neutral[50] : neutral[900];
  const textMuted = dark ? neutral[300] : neutral[600];
  const border = dark ? neutral[800] : neutral[200];

  // Brand colours: the tenant's exact seed in light mode; a lighter step in dark mode, where the
  // seed usually sits too dark against the page. Then the last-defence check: large text / UI
  // components need 3:1 against their label colour.
  const pick = (ramp: Ramp, seed: string, name: string) => {
    const preferred: RampStep = dark ? (RAMP_STEPS.find((s) => s === 400) as RampStep) : anchorOf(ramp, seed);
    const label = onColor(ramp[preferred]);
    const step = readableStep(ramp, preferred, label, 3);
    if (step !== preferred) adjusted.push(`${name} ${ramp[preferred]} → ${ramp[step]}`);
    return { color: ramp[step], on: onColor(ramp[step]) };
  };
  const p = pick(pr, primary, 'primary');
  const a = pick(ac, accent, 'accent');

  return {
    mode, primary: p.color, onPrimary: p.on, accent: a.color, onAccent: a.on,
    background, surface: surfaceTone, text, textMuted, border,
    ramps: { primary: pr, accent: ac, neutral }, adjusted,
  };
};

/** The resolved tokens as CSS custom properties, for anything outside MUI (charts, maps, iframes). */
export const cssVariables = (t: SemanticTokens): Record<string, string> => {
  const vars: Record<string, string> = {
    '--ce-color-primary': t.primary, '--ce-color-on-primary': t.onPrimary,
    '--ce-color-accent': t.accent, '--ce-color-on-accent': t.onAccent,
    '--ce-color-background': t.background, '--ce-color-surface': t.surface,
    '--ce-color-text': t.text, '--ce-color-text-muted': t.textMuted, '--ce-color-border': t.border,
  };
  for (const [name, ramp] of Object.entries(t.ramps)) {
    for (const s of RAMP_STEPS) vars[`--ce-${name}-${s}`] = ramp[s];
  }
  return vars;
};
