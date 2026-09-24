import { describe, expect, it } from 'vitest';
import { contrast } from './color';
import { cssVariables, resolveTokens } from './tokens';
import { STYLE_PACKS, registryMismatch, stylePack } from './stylePacks';
import manifest from './registry-manifest.json';
import { buildTheme, experienceOf } from '../theme';

describe('token pipeline', () => {
  it('keeps the tenant colour exactly in light mode when it already reads', () => {
    const t = resolveTokens({ mode: 'light', primary: '#0057ff', accent: '#00a3a3' });
    expect(t.primary).toBe('#0057ff');
    expect(t.onPrimary).toBe('#ffffff');
    expect(t.adjusted).toEqual([]);
  });

  it('every brand/label pair reads at 3:1 or better, in both modes, for awkward seeds', () => {
    for (const mode of ['light', 'dark'] as const) {
      for (const seed of ['#ffeb3b', '#00ff00', '#0057ff', '#111111', '#f0f0f0', '#c9a227']) {
        const t = resolveTokens({ mode, primary: seed, accent: seed });
        expect(contrast(t.primary, t.onPrimary)).toBeGreaterThanOrEqual(3);
        expect(contrast(t.text, t.background)).toBeGreaterThanOrEqual(7);
        expect(contrast(t.text, t.surface)).toBeGreaterThanOrEqual(7);
      }
    }
  });

  it('exposes tokens and ramps as CSS custom properties', () => {
    const vars = cssVariables(resolveTokens({ mode: 'dark', primary: '#008000', accent: '#c9a227' }));
    expect(vars['--ce-color-primary']).toMatch(/^#[0-9a-f]{6}$/);
    expect(vars['--ce-primary-950']).toBeDefined();
    expect(vars['--ce-neutral-50']).toBeDefined();
  });

  it('ignores a surface colour that does not suit the mode', () => {
    const dark = resolveTokens({ mode: 'dark', primary: '#0057ff', accent: '#0057ff', surface: '#ffffff' });
    expect(dark.surface).not.toBe('#ffffff');
    const light = resolveTokens({ mode: 'light', primary: '#0057ff', accent: '#0057ff', surface: '#fdfaf3' });
    expect(light.surface).toBe('#fdfaf3');
  });
});

describe('style packs', () => {
  it('the registry and the manifest list the same packs', () => {
    expect(registryMismatch()).toEqual([]);
    expect(Object.keys(STYLE_PACKS).sort()).toEqual([...manifest.stylePacks].sort());
  });

  it('an unknown key falls back to the default pack', () => {
    expect(stylePack('holographic').key).toBe('default');
  });

  it('each pack shapes the theme its own way', () => {
    const t = (uiStyle: string) => buildTheme({ mode: 'light', primaryColor: '#0057ff', accentColor: '#00a3a3', uiStyle } as never);
    const card = (uiStyle: string) => (t(uiStyle).components!.MuiCard!.styleOverrides!.root as Record<string, unknown>);
    expect(card('brutalist').borderRadius).toBe(0);
    expect(String(card('brutalist').boxShadow)).toMatch(/^6px 6px 0/);
    expect(String(card('glass').backdropFilter)).toContain('blur');
    expect(card('luxury').borderRadius).toBe(2);
    expect(t('luxury').typography.h1.fontFamily).toContain('Playfair');
    expect(experienceOf(t('glass')).pack).toBe('glass');
  });

  it('carries the site layout for the page templates', () => {
    expect(experienceOf(buildTheme({ siteLayout: 'corporate' } as never)).siteLayout).toBe('corporate');
    expect(experienceOf(buildTheme(null)).siteLayout).toBe('marketplace');
  });
});
