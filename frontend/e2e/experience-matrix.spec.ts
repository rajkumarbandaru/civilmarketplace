import { Page } from '@playwright/test';
import { test, expect, mockApi, json } from './fixtures';

/**
 * The reference-configuration matrix (architecture 05 §7.4 rule 6): tenants A, B and C — each a
 * different colour × style pack × site layout — rendered from this one build, in light and dark,
 * on desktop and mobile. Only the configuration differs. Screenshots guard against visual
 * regressions; the assertions check each axis actually took effect and stays accessible.
 */

const PRESETS = {
  A: { primaryColor: '#0057ff', accentColor: '#00a3ff', borderRadius: 20, uiStyle: 'glass', layoutStyle: 'topbar',
       siteLayout: 'marketplace', buttonStyle: 'solid', brandName: 'Aurora' },
  B: { primaryColor: '#0b8043', accentColor: '#1a73e8', borderRadius: 8, uiStyle: 'material', layoutStyle: 'sidebar-left',
       siteLayout: 'corporate', buttonStyle: 'solid', brandName: 'Evergreen' },
  C: { primaryColor: '#111111', accentColor: '#c9a227', borderRadius: 2, uiStyle: 'luxury', layoutStyle: 'topbar',
       siteLayout: 'ecommerce', buttonStyle: 'solid', brandName: 'Onyx' },
} as const;

const EXPECT = {
  A: { blocks: ['hero', 'stats', 'services', 'how-it-works', 'cta'], cardRadius: '28px' },
  B: { blocks: ['hero', 'how-it-works', 'stats', 'cta'], cardRadius: '8px' },
  C: { blocks: ['hero', 'services', 'cta'], cardRadius: '2px' },
};

const theme = (p: keyof typeof PRESETS, mode: 'light' | 'dark') => ({
  scopeKey: 'PLATFORM', mode, surfaceColor: null, sidebarColor: null, fontFamily: null, logoUrl: null,
  density: 'comfortable', version: 1, ...PRESETS[p],
});

/** WCAG contrast of two computed CSS colours, measured in the page. */
const contrastIn = (page: Page, selector: string) => page.evaluate((sel) => {
  const el = document.querySelector(sel) as HTMLElement | null;
  if (!el) return 0;
  const cs = getComputedStyle(el);
  const parse = (c: string) => (c.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
  const lum = ([r, g, b]: number[]) => [r, g, b].map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; })
    .reduce((a, v, i) => a + v * [0.2126, 0.7152, 0.0722][i], 0);
  let bg = cs.backgroundColor;
  const img = cs.backgroundImage.match(/rgba?\([^)]+\)/);
  if ((bg === 'rgba(0, 0, 0, 0)' || bg === 'transparent') && img) bg = img[0];
  const a = lum(parse(cs.color)), b = lum(parse(bg));
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}, selector);

for (const preset of ['A', 'B', 'C'] as const) {
  for (const mode of ['light', 'dark'] as const) {
    for (const device of ['desktop', 'mobile'] as const) {
      test(`${preset} · ${mode} · ${device}`, async ({ page }) => {
        await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: mode });
        await page.setViewportSize(device === 'desktop' ? { width: 1280, height: 900 } : { width: 390, height: 844 });
        await mockApi(page, '/ui-config/public/theme', theme(preset, mode));
        await page.goto('/');

        const home = page.getByTestId('home');
        await expect(home).toHaveAttribute('data-site-layout', PRESETS[preset].siteLayout);
        // The layout decides which blocks appear and in what order.
        const order = await page.locator('[data-block]').evaluateAll((els) => els.map((e) => e.getAttribute('data-block')));
        expect(order).toEqual(EXPECT[preset].blocks);

        // The style pack shaped the cards; the palette reached the page.
        const card = page.locator('.MuiCard-root').first();
        if (await card.count()) await expect(card).toHaveCSS('border-top-left-radius', EXPECT[preset].cardRadius);
        const bodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundImage);
        if (preset === 'A') expect(bodyBg).toContain('radial-gradient');
        const primaryVar = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--ce-color-primary').trim());
        expect(primaryVar).toMatch(/^#[0-9a-f]{6}$/);

        // Accessibility holds in every combination: the main call to action reads at ≥ 3:1.
        expect(await contrastIn(page, '[data-block="hero"] .MuiButton-contained')).toBeGreaterThanOrEqual(3);

        await page.addStyleTag({ content: '[data-decorative]{display:none!important}' });
        await expect(page).toHaveScreenshot(`${preset}-${mode}-${device}.png`, { fullPage: false, maxDiffPixelRatio: 0.01 });
      });
    }
  }
}

test('a preview link shows an unpublished theme with a banner, until it is exited', async ({ page }) => {
  await mockApi(page, '/ui-config/public/theme', theme('A', 'light'));
  await mockApi(page, '/ui-config/public/preview/tok-1', theme('C', 'dark'));
  await page.goto('/?preview=tok-1');
  await expect(page.getByTestId('preview-banner')).toContainText('not published');
  await expect(page.getByTestId('home')).toHaveAttribute('data-site-layout', 'ecommerce');

  await page.goto('/services');
  await expect(page.getByTestId('preview-banner')).toBeVisible();

  await page.getByRole('button', { name: 'Exit preview' }).click();
  await page.goto('/');
  await expect(page.getByTestId('preview-banner')).toHaveCount(0);
  await expect(page.getByTestId('home')).toHaveAttribute('data-site-layout', 'marketplace');
});

test('an expired preview link says so rather than silently showing the live theme', async ({ page }) => {
  await mockApi(page, '/ui-config/public/preview/old', (route) => json(route, { message: 'This preview link has expired.' }, 404));
  await page.goto('/?preview=old');
  await expect(page.getByTestId('preview-banner')).toContainText('expired');
});
