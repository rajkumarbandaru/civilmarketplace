/**
 * Colour maths for the token pipeline (architecture 05 §2.1): hex ↔ OKLCH, tonal ramps generated
 * perceptually, and WCAG contrast. OKLCH keeps lightness steps even across hues, so a tenant's one
 * brand colour yields hover, pressed and disabled tones that look like the same family — something
 * lightening in RGB or HSL does not do (yellow washes out, blue turns purple).
 */

export interface Oklch { l: number; c: number; h: number; }

const clamp = (v: number, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v));

export const parseHex = (hex: string): [number, number, number] | null => {
  const h = hex.trim().replace('#', '');
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h.slice(0, 6);
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null;
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16) / 255) as [number, number, number];
};

const toHex = (rgb: [number, number, number]) =>
  `#${rgb.map((v) => Math.round(clamp(v) * 255).toString(16).padStart(2, '0')).join('')}`;

const srgbToLinear = (v: number) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
const linearToSrgb = (v: number) => (v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055);

export const hexToOklch = (hex: string): Oklch | null => {
  const rgb = parseHex(hex);
  if (!rgb) return null;
  const [r, g, b] = rgb.map(srgbToLinear);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
  const c = Math.sqrt(A * A + B * B);
  const h = ((Math.atan2(B, A) * 180) / Math.PI + 360) % 360;
  return { l: L, c, h };
};

const oklchToLinear = ({ l: L, c, h }: Oklch): [number, number, number] => {
  const a = c * Math.cos((h * Math.PI) / 180);
  const b = c * Math.sin((h * Math.PI) / 180);
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
};

const inGamut = (rgb: [number, number, number]) => rgb.every((v) => v >= -0.0001 && v <= 1.0001);

/** OKLCH → hex, reducing chroma until the colour fits sRGB (keeps the hue and lightness honest). */
export const oklchToHex = (color: Oklch): string => {
  let c = color.c;
  let lin = oklchToLinear({ ...color, c });
  for (let i = 0; i < 30 && !inGamut(lin); i++) {
    c *= 0.9;
    lin = oklchToLinear({ ...color, c });
  }
  return toHex(lin.map(linearToSrgb) as [number, number, number]);
};

export const RAMP_STEPS = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] as const;
export type RampStep = typeof RAMP_STEPS[number];
export type Ramp = Record<RampStep, string>;

/** Target lightness per step: 50 is nearly white, 950 nearly black; 500 is where the seed sits. */
const LIGHTNESS: Record<RampStep, number> = {
  50: 0.975, 100: 0.945, 200: 0.89, 300: 0.81, 400: 0.72, 500: 0.63, 600: 0.54, 700: 0.46, 800: 0.38, 900: 0.3, 950: 0.22,
};

/**
 * A tonal ramp from one seed. The seed's own lightness decides which step it lands on (its exact
 * colour is kept there), and the other steps keep its hue with chroma easing off at the extremes,
 * which is what keeps pale tints from turning neon and dark shades from going muddy.
 */
export const generateRamp = (seedHex: string): Ramp => {
  const seed = hexToOklch(seedHex) ?? hexToOklch('#667eea')!;
  const anchor = RAMP_STEPS.reduce((best, s) =>
    Math.abs(LIGHTNESS[s] - seed.l) < Math.abs(LIGHTNESS[best] - seed.l) ? s : best, 500 as RampStep);
  const ramp = {} as Ramp;
  for (const step of RAMP_STEPS) {
    if (step === anchor) {
      ramp[step] = oklchToHex(seed);
      continue;
    }
    const l = LIGHTNESS[step];
    const edge = Math.min(Math.abs(l - 0.975), Math.abs(l - 0.22)) / 0.4; // 0 at the ends, 1 mid-ramp
    ramp[step] = oklchToHex({ l, c: seed.c * clamp(0.25 + 0.85 * edge, 0.2, 1), h: seed.h });
  }
  return ramp;
};

const luminance = (hex: string) => {
  const rgb = parseHex(hex);
  if (!rgb) return 0;
  const [r, g, b] = rgb.map(srgbToLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

/** WCAG 2.x contrast ratio. */
export const contrast = (a: string, b: string) => {
  const la = luminance(a), lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

/** White or near-black text, whichever reads better on the colour. */
export const onColor = (bg: string) => (contrast(bg, '#ffffff') >= contrast(bg, '#111827') ? '#ffffff' : '#111827');

/**
 * The runtime contrast fallback (05 §3, last defence): the nearest ramp step on which `text`
 * reaches `min`, searching darker first for white text and lighter first for dark text. The
 * tenant's exact colour is used whenever it already passes.
 */
export const readableStep = (ramp: Ramp, preferred: RampStep, text: string, min = 4.5): RampStep => {
  if (contrast(ramp[preferred], text) >= min) return preferred;
  const idx = RAMP_STEPS.indexOf(preferred);
  const dark = contrast('#000000', text) < contrast('#ffffff', text); // text is light → go darker
  const order = dark
    ? [...RAMP_STEPS.slice(idx + 1), ...RAMP_STEPS.slice(0, idx).reverse()]
    : [...RAMP_STEPS.slice(0, idx).reverse(), ...RAMP_STEPS.slice(idx + 1)];
  return order.find((s) => contrast(ramp[s], text) >= min) ?? preferred;
};
