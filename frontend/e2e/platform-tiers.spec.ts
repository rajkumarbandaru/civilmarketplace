import { test, expect, mockApi, json, signInAs } from './fixtures';

/**
 * RK Technologies (the platform) and each tenant (CivEngMarket, a B2B tenant, …) have separate
 * staff. Platform staff sign in on the `platform` console and run tenants; a tenant's owner signs in
 * on the tenant's own address and runs only that business.
 */

const workspace = (tenantKey: string, name: string, modules: string[]) => ({
  tenantKey, name, status: 'ACTIVE', vertical: 'CIVIL_MARKETPLACE', modules, branding: { brandName: name },
});

const theme = {
  scopeKey: 'PLATFORM', mode: 'light', primaryColor: '#1e40af', accentColor: null, surfaceColor: null, sidebarColor: null,
  borderRadius: 12, fontFamily: null, brandName: null, logoUrl: null, uiStyle: 'default', buttonStyle: 'solid',
  layoutStyle: 'sidebar-left', density: 'comfortable', version: 1,
};

const item = (key: string, label: string, path: string, icon: string, menuGroup: string, sortOrder: number) => ({
  key, label, path, icon, section: 'Platform', menuGroup, sortOrder, exactMatch: key === 'admin-overview',
});
const overview = item('admin-overview', 'Dashboard', '/admin', 'Dashboard', 'Overview', 100);
const users = item('admin-users', 'Users', '/admin/users', 'People', 'People', 110);
const bookings = item('admin-bookings', 'Bookings', '/admin/bookings', 'BookOnline', 'Operations', 130);
const tenants = item('admin-tenants', 'Tenants', '/admin/tenants', 'Domain', 'System', 175);

const snapshot = (role: string, menu: unknown[]) => ({
  userId: 1, role, menu, theme, timezone: null, dateFormat: null, landingPath: null,
});

const tenantRow = (tenantKey: string, name: string) => ({
  tenantKey, name, subdomain: tenantKey, customDomain: null, status: 'ACTIVE', contactEmail: `ops@${tenantKey}.in`,
  plan: 'enterprise', vertical: 'CIVIL_MARKETPLACE', modules: ['auth', 'bookings'], menuOverrides: [], landingPath: null,
  branding: null, createdAt: '2026-09-26T10:00:00',
});

test.describe('Platform and tenant tiers', () => {
  test('a platform owner on the RK console runs tenants, including CivEngMarket', async ({ page }) => {
    await signInAs(page, { id: 1, name: 'RK Owner', email: 'owner@rktech.test', role: 'PLATFORM_OWNER' });
    await mockApi(page, '/tenant-resolution/current',
      workspace('platform', 'RK Technologies', ['auth', 'users', 'admin', 'tenantadmin']));
    await mockApi(page, /\/api\/v1\/ui-config\/me(\?.*)?$/, snapshot('PLATFORM_OWNER', [overview, users, tenants]));
    await mockApi(page, '/tenants', [tenantRow('platform', 'RK Technologies'), tenantRow('civengmarket', 'CivEngMarket')]);

    await page.goto('/admin/tenants');

    const badge = page.getByTestId('console-tier');
    await expect(badge).toHaveAttribute('data-tier', 'platform');
    await expect(badge).toContainText('RK Technologies · Platform console');
    await expect(badge).toContainText('Platform owner');
    await expect(page.getByRole('link', { name: 'Tenants' }).or(page.getByRole('button', { name: 'Tenants' })).first())
      .toBeVisible();
    await expect(page.getByText('CivEngMarket').first()).toBeVisible();
    // The console runs no marketplace of its own.
    await expect(page.getByText('Bookings', { exact: true })).toHaveCount(0);
  });

  test('a tenant owner on CivEngMarket runs their business and never sees tenant administration', async ({ page }) => {
    await signInAs(page, { id: 2, name: 'Civ Owner', email: 'owner@civengmarket.test', role: 'TENANT_OWNER' });
    await mockApi(page, '/tenant-resolution/current',
      workspace('civengmarket', 'CivEngMarket', ['auth', 'users', 'admin', 'bookings']));
    await mockApi(page, /\/api\/v1\/ui-config\/me(\?.*)?$/, snapshot('TENANT_OWNER', [overview, users, bookings]));

    await page.goto('/admin');

    const badge = page.getByTestId('console-tier');
    await expect(badge).toHaveAttribute('data-tier', 'tenant');
    await expect(badge).toContainText('CivEngMarket · Tenant admin');
    await expect(badge).toContainText('Tenant owner');
    await expect(page.getByText('Bookings', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Tenants', { exact: true })).toHaveCount(0);
  });

  test('a tenant owner who reaches the tenants screen is told it belongs to platform staff', async ({ page }) => {
    await signInAs(page, { id: 2, name: 'Civ Owner', email: 'owner@civengmarket.test', role: 'TENANT_OWNER' });
    await mockApi(page, '/tenant-resolution/current',
      workspace('civengmarket', 'CivEngMarket', ['auth', 'users', 'admin', 'bookings']));
    await mockApi(page, '/tenants', (route) =>
      json(route, { message: 'Tenant administration is restricted to RK platform staff' }, 403));

    await page.goto('/admin/tenants');

    await expect(page.getByTestId('tenants-forbidden')).toContainText("platform's own staff");
  });

  test('platform support is labelled as such on the console', async ({ page }) => {
    await signInAs(page, { id: 3, name: 'RK Support', email: 'support@rktech.test', role: 'PLATFORM_SUPPORT' });
    await mockApi(page, '/tenant-resolution/current',
      workspace('platform', 'RK Technologies', ['auth', 'users', 'admin', 'tenantadmin']));
    await mockApi(page, /\/api\/v1\/ui-config\/me(\?.*)?$/, snapshot('PLATFORM_SUPPORT', [overview, tenants]));
    await mockApi(page, '/tenants', [tenantRow('civengmarket', 'CivEngMarket')]);

    await page.goto('/admin/tenants');

    await expect(page.getByTestId('console-tier')).toContainText('Platform support');
  });

  test('adding a user on the RK console offers platform roles only, no member roles', async ({ page }) => {
    await signInAs(page, { id: 1, name: 'RK Owner', email: 'owner@rktech.test', role: 'PLATFORM_OWNER' });
    await mockApi(page, '/tenant-resolution/current',
      workspace('platform', 'RK Technologies', ['auth', 'users', 'admin', 'tenantadmin']));
    await mockApi(page, '/admin/users', { success: true, data: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    // auth-service lists only the roles that exist in this tenant: the platform ones.
    await mockApi(page, '/admin/users/roles', { success: true, data: ['PLATFORM_ADMIN', 'PLATFORM_OWNER', 'PLATFORM_SUPPORT']
      .map((name) => ({ name, description: '', systemRole: true, userCount: 0 })) });

    await page.goto('/admin/users');
    await page.getByRole('button', { name: 'Add User' }).click();
    await page.getByRole('dialog').getByTestId('role-select').getByRole('combobox').click();

    const options = page.getByRole('option');
    await expect(options).toHaveText(['Platform admin', 'Platform owner', 'Platform support']);
    await expect(page.getByRole('option', { name: 'Customer' })).toHaveCount(0);
  });

  test('the retired SUPER_ADMIN role no longer opens the console', async ({ page }) => {
    await signInAs(page, { id: 4, name: 'Old', email: 'old@example.com', role: 'SUPER_ADMIN' });
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
