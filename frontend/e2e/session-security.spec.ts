import { test, expect, mockApi, json, signInAs, customer } from './fixtures';

test.describe('Session security', () => {
  test('sign out revokes the session on the server, not just in the browser', async ({ page, context }) => {
    await mockApi(page, '/auth/login', { user: customer, accessToken: 'a1', refreshToken: 'r1' });
    await mockApi(page, '/auth/refresh/device', { refreshToken: 'device-1' });
    let logoutCall: { body: unknown; auth: string | undefined } | undefined;
    await mockApi(page, '/auth/logout', (route) => {
      logoutCall = { body: route.request().postDataJSON(), auth: route.request().headers().authorization };
      return json(route, { success: true });
    });

    await page.goto('/login');
    await page.getByLabel('Email').fill(customer.email);
    await page.getByLabel('Password', { exact: true }).fill('Secret123!');
    await page.getByRole('checkbox', { name: 'Keep me signed in on this device' }).check();
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect.poll(() => page.evaluate(() => localStorage.getItem('civeng.rememberedSession'))).toBe('device-1');

    await page.locator('header button:has(.MuiAvatar-root)').click();
    await page.getByRole('menuitem', { name: 'Logout' }).click();

    await expect.poll(() => logoutCall).toBeTruthy();
    expect(logoutCall).toEqual({ body: { refreshTokens: ['r1', 'device-1'] }, auth: 'Bearer a1' });
    expect(await page.evaluate(() => localStorage.getItem('civeng.rememberedSession'))).toBeNull();

    // And a new tab no longer signs itself in.
    const tab = await context.newPage();
    await tab.route('**/api/**', (route) => json(route, {}, 404));
    await tab.goto('/dashboard');
    await expect(tab).toHaveURL(/\/login$/);
  });

  test('simultaneous 401s share one token refresh', async ({ page }) => {
    await signInAs(page, customer);
    let refreshes = 0;
    // Every API call made with the expired token is refused; the refreshed one is accepted.
    await page.route('**/api/v1/**', (route) => {
      const auth = route.request().headers().authorization;
      return auth === 'Bearer e2e-access'
        ? json(route, { message: 'expired' }, 401)
        : json(route, { message: 'not mocked' }, 404);
    });
    await mockApi(page, '/auth/refresh', async (route) => {
      refreshes += 1;
      // Hold the response so the other 401s arrive while this refresh is still in flight.
      await new Promise((r) => setTimeout(r, 300));
      return json(route, { accessToken: 'fresh', refreshToken: 'r-next' });
    });

    await page.goto('/dashboard');
    await expect(page.getByText(/Welcome back, Asha/)).toBeVisible();
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('accessToken'))).toBe('fresh');
    await page.waitForTimeout(500);
    expect(refreshes).toBe(1);
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('a refused refresh signs the tab out', async ({ page }) => {
    await signInAs(page, customer);
    await page.route('**/api/v1/**', (route) => json(route, { message: 'expired' }, 401));
    await page.goto('/dashboard');
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('accessToken'))).toBeNull();
    await expect(page).toHaveURL(/\/login$/);
  });
});
