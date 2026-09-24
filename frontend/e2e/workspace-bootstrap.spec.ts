import { test, expect, mockApi, json } from './fixtures';

/** The app learns which workspace its address serves before anyone signs in. */
test.describe('Workspace bootstrap', () => {
  test('the sign-in screen carries the workspace name and logo', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', {
      tenantKey: 'acme', name: 'Acme Builders', status: 'ACTIVE', vertical: 'CIVIL_MARKETPLACE', modules: [],
      branding: { brandName: 'Acme', logoUrl: '/favicon.svg' },
    });
    await page.goto('/login');
    await expect(page.getByText('Sign in to Acme')).toBeVisible();
    await expect(page.getByAltText('Acme logo')).toBeVisible();
    await expect(page).toHaveTitle('Acme');
  });

  test('an address that serves no workspace says so', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', (route) =>
      json(route, { success: false, message: 'No workspace is served at nope.localhost', status: 404 }, 404));
    await page.goto('/login');
    await expect(page.getByTestId('workspace-unavailable')).toContainText('No workspace at this address');
    await expect(page.getByRole('button', { name: 'Sign In' })).toHaveCount(0);
  });

  test('a suspended workspace says so', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', (route) =>
      json(route, { success: false, message: 'This workspace is suspended', status: 503 }, 503));
    await page.goto('/');
    await expect(page.getByTestId('workspace-unavailable')).toContainText('This workspace is unavailable');
  });

  test('if the lookup itself fails the app still works, unbranded', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', (route) => route.abort());
    await page.goto('/login');
    await expect(page.getByText('Sign in to your account')).toBeVisible();
  });
});
