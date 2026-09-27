import { test, expect, mockApi, signInAs } from './fixtures';

/** The RK Technologies platform address: its own public page, staff-only sign-in, and a platform console. */

const rk = {
  tenantKey: 'platform', name: 'RK Technologies', status: 'ACTIVE', vertical: 'CIVIL_MARKETPLACE',
  modules: ['auth', 'users', 'admin', 'tenantadmin'], branding: { brandName: 'RK Technologies' },
};
const civ = {
  tenantKey: 'civengmarket', name: 'CivEngMarket', status: 'ACTIVE', vertical: 'CIVIL_MARKETPLACE',
  modules: ['auth', 'users', 'admin', 'bookings'], branding: { brandName: 'CivEngMarket' },
};

const tenant = (tenantKey: string, name: string, status: string, plan: string, createdAt: string) => ({
  tenantKey, name, subdomain: tenantKey, customDomain: null, status, contactEmail: null, plan,
  vertical: 'CIVIL_MARKETPLACE', modules: [], menuOverrides: [], landingPath: null, branding: null, createdAt,
});
const tenants = [
  tenant('platform', 'RK Technologies', 'ACTIVE', 'enterprise', '2026-01-01T00:00:00'),
  tenant('civengmarket', 'CivEngMarket', 'ACTIVE', 'enterprise', '2026-09-26T09:00:00'),
  tenant('acme', 'Acme Retail', 'PROVISIONING', 'professional', '2026-09-26T10:00:00'),
];

const owner = { id: 1, name: 'RK Owner', email: 'owner@rktech.test', role: 'PLATFORM_OWNER' };

test.describe('RK platform web view', () => {
  test('visitors see RK Technologies, not the marketplace, and can reach staff sign-in', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', rk);
    await page.goto('/');

    await expect(page.getByTestId('platform-landing')).toBeVisible();
    await expect(page.getByTestId('platform-brand')).toHaveText('RK Technologies');
    await expect(page.getByTestId('platform-name')).toHaveText('RK Technologies');
    await expect(page.getByTestId('platform-diagram')).toContainText('Platform factory');
    // The company's page names no tenant.
    await expect(page.getByText(/CivEngMarket/)).toHaveCount(0);
    // The marketplace's FAQ chat has nothing to say on RK's own site.
    await expect(page.getByRole('button', { name: 'Open support chat' })).toHaveCount(0);

    await page.getByRole('main').getByRole('link', { name: 'Staff sign in' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText('RK Technologies staff sign-in')).toBeVisible();
    await expect(page.getByTestId('platform-signin-note')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Register' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Back to RK Technologies' })).toBeVisible();
  });

  test('renaming the platform (its published brand) renames every place it appears', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', { ...rk, name: 'Acme Platforms', branding: { brandName: 'Acme Platforms' } });
    await page.goto('/');
    await expect(page.getByTestId('platform-brand')).toHaveText('Acme Platforms');
    await expect(page.getByTestId('platform-name')).toHaveText('Acme Platforms');
    await expect(page.getByText(/RK Technologies/)).toHaveCount(0);
    await page.goto('/login');
    await expect(page.getByText('Acme Platforms staff sign-in')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Back to Acme Platforms' })).toBeVisible();
  });

  test('marketplace addresses and sign-up are not served on the platform host', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', rk);
    await page.goto('/services');
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByTestId('platform-landing')).toBeVisible();

    await page.goto('/register');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('a tenant address still shows the marketplace home', async ({ page }) => {
    await mockApi(page, '/tenant-resolution/current', civ);
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Register' }).first()).toBeVisible();
    await expect(page.getByTestId('platform-landing')).toHaveCount(0);
  });

  test('the platform console opens on the platform dashboard', async ({ page }) => {
    await signInAs(page, owner);
    await mockApi(page, '/tenant-resolution/current', rk);
    await mockApi(page, '/tenants', tenants);
    await mockApi(page, '/tenants/drafts', []);
    await page.goto('/admin');

    await expect(page.getByTestId('platform-dashboard')).toBeVisible();
    await expect(page.getByTestId('stat-tenants')).toHaveText('2');
    await expect(page.getByTestId('stat-active')).toHaveText('1');
    await expect(page.getByTestId('platform-recent-tenants')).toContainText('Acme Retail');
    // No marketplace catalogue search on the platform console.
    await expect(page.getByLabel('search services, materials and equipment')).toHaveCount(0);

    await page.getByRole('link', { name: 'New tenant' }).first().click();
    await expect(page).toHaveURL(/\/admin\/tenants\/new$/);
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  test('plans and platform analytics are RK console screens', async ({ page }) => {
    await signInAs(page, owner);
    await mockApi(page, '/tenant-resolution/current', rk);
    await mockApi(page, '/tenants', tenants);
    await mockApi(page, '/tenants/plans', {
      plans: [
        { key: 'professional', version: 2, name: 'Professional', features: ['bookings', 'projects'], limits: { 'staff.seats': 50 } },
        { key: 'enterprise', version: 2, name: 'Enterprise', features: ['bookings', 'procurement'], limits: {} },
      ],
      addOns: [{ key: 'seats-10', name: '+10 staff users', features: [], increments: { 'staff.seats': 10 } }],
      limits: { 'staff.seats': 'Staff users' },
      baseModules: ['auth', 'users'],
    });
    await page.goto('/admin/plans');
    await expect(page.getByTestId('plan-enterprise')).toContainText('CivEngMarket');
    await expect(page.getByTestId('plan-professional')).toContainText('Acme Retail');
    await expect(page.getByTestId('plan-enterprise')).toContainText('Unlimited');

    await page.goto('/admin/platform-analytics');
    await expect(page.getByTestId('platform-analytics')).toBeVisible();
  });

  test('a tenant owner cannot open the RK console screens', async ({ page }) => {
    await signInAs(page, { id: 2, name: 'Civ Owner', email: 'owner@civ.test', role: 'TENANT_OWNER' });
    await mockApi(page, '/tenant-resolution/current', civ);
    await page.goto('/admin/plans');
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByTestId('platform-dashboard')).toHaveCount(0);
  });
});
