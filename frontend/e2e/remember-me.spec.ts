import { test, expect, mockApi, json, customer } from './fixtures';
import type { BrowserContext, Page } from '@playwright/test';

/**
 * "Keep me signed in on this device": the live session is per-tab (sessionStorage), so the only
 * way to observe the checkbox is a brand-new tab in the same browser, which shares localStorage.
 */

const REMEMBERED = 'civeng.rememberedSession';

const signIn = async (page: Page, remember: boolean) => {
  await mockApi(page, '/auth/login', { user: customer, accessToken: 'a1', refreshToken: 'r1' });
  await mockApi(page, '/auth/refresh/device', { refreshToken: 'device-1' });
  await page.goto('/login');
  await page.getByLabel('Email').fill(customer.email);
  await page.getByLabel('Password', { exact: true }).fill('Secret123!');
  if (remember) await page.getByRole('checkbox', { name: 'Keep me signed in on this device' }).check();
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
};

/** A fresh tab with the same API mocks the fixture gives the first one. */
const newTab = async (context: BrowserContext, onRefresh?: (body: unknown) => void) => {
  const tab = await context.newPage();
  await tab.route('**/api/**', (route) => json(route, { message: 'not mocked' }, 404));
  await mockApi(tab, '/auth/refresh', (route) => {
    onRefresh?.(route.request().postDataJSON());
    return json(route, { user: customer, accessToken: 'a2', refreshToken: 'r2' });
  });
  await mockApi(tab, '/auth/refresh/device', { refreshToken: 'device-2' });
  return tab;
};

test.describe('Keep me signed in', () => {
  test('ticked: a new tab signs straight back in and rotates the remembered token', async ({ page, context }) => {
    await signIn(page, true);
    // The device's own token, not the tab's r1: sharing one would trip reuse detection.
    await expect.poll(() => page.evaluate((k) => localStorage.getItem(k), REMEMBERED)).toBe('device-1');

    let refreshedWith: unknown;
    const tab = await newTab(context, (body) => { refreshedWith = body; });
    await tab.goto('/dashboard');
    await expect(tab).toHaveURL(/\/dashboard$/);
    await expect(tab.getByText(/Welcome back, Asha/)).toBeVisible();
    expect(refreshedWith).toEqual({ refreshToken: 'device-1' });
    await expect.poll(() => tab.evaluate((k) => localStorage.getItem(k), REMEMBERED)).toBe('device-2');
    expect(await tab.evaluate(() => sessionStorage.getItem('refreshToken'))).toBe('r2');
  });

  test('unticked: nothing is remembered and a new tab is signed out', async ({ page, context }) => {
    await signIn(page, false);
    expect(await page.evaluate((k) => localStorage.getItem(k), REMEMBERED)).toBeNull();

    const tab = await newTab(context);
    await tab.goto('/dashboard');
    await expect(tab).toHaveURL(/\/login$/);
  });

  test('a remembered token the server rejects is forgotten', async ({ page, context }) => {
    await signIn(page, true);
    await expect.poll(() => page.evaluate((k) => localStorage.getItem(k), REMEMBERED)).toBe('device-1');
    const tab = await context.newPage();
    await tab.route('**/api/**', (route) => json(route, { message: 'expired' }, 401));
    await tab.goto('/dashboard');
    await expect(tab).toHaveURL(/\/login$/);
    await expect.poll(() => tab.evaluate((k) => localStorage.getItem(k), REMEMBERED)).toBeNull();
  });
});
