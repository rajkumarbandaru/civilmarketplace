import { describe, expect, it } from 'vitest';
import { RAMP_STEPS, contrast, generateRamp, hexToOklch, oklchToHex, onColor, readableStep } from './color';

describe('OKLCH colour maths', () => {
  it('round-trips colours through OKLCH', () => {
    for (const hex of ['#0057ff', '#008000', '#111827', '#ffffff', '#ffeb3b', '#e11d48']) {
      expect(oklchToHex(hexToOklch(hex)!)).toBe(hex);
    }
  });

  it('matches reference OKLCH values', () => {
    const blue = hexToOklch('#0000ff')!;
    expect(blue.l).toBeCloseTo(0.452, 2);
    expect(blue.c).toBeCloseTo(0.313, 2);
    expect(blue.h).toBeCloseTo(264.05, 0);
  });

  it('builds a ramp that keeps the seed and gets steadily darker', () => {
    const ramp = generateRamp('#0057ff');
    expect(Object.values(ramp)).toContain('#0057ff');
    const lightness = RAMP_STEPS.map((s) => hexToOklch(ramp[s])!.l);
    lightness.slice(1).forEach((l, i) => expect(l).toBeLessThan(lightness[i]));
    expect(hexToOklch(ramp[50])!.l).toBeGreaterThan(0.95);
    expect(hexToOklch(ramp[950])!.l).toBeLessThan(0.3);
  });

  it('keeps the hue across the ramp (no blue turning purple)', () => {
    const ramp = generateRamp('#0057ff');
    const seedHue = hexToOklch('#0057ff')!.h;
    for (const s of [200, 400, 700, 900] as const) expect(Math.abs(hexToOklch(ramp[s])!.h - seedHue)).toBeLessThan(8);
  });

  it('computes WCAG contrast and picks readable label colours', () => {
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(onColor('#0057ff')).toBe('#ffffff');
    expect(onColor('#ffeb3b')).toBe('#111827');
  });

  it('falls back to the nearest step where the label reads', () => {
    const ramp = generateRamp('#ffeb3b');
    const anchor = RAMP_STEPS.find((s) => ramp[s] === '#ffeb3b')!;
    const step = readableStep(ramp, anchor, '#ffffff', 3);
    expect(step).not.toBe(anchor);
    expect(contrast(ramp[step], '#ffffff')).toBeGreaterThanOrEqual(3);
  });
});
