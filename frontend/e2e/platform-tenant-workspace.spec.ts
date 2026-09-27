import { test, expect, mockApi, json, signInAs } from './fixtures';

/**
 * Platform staff working inside a customer tenant from the RK console: the tenant's own screens,
 * on the tenant's data (the gateway switches every staff-screen call carrying X-Acting-Tenant), and
 * the platform deciding which roles see each item in that tenant's menu.
 */

const workspace = { tenantKey: 'platform', name: 'RK Technologies', status: 'ACTIVE', vertical: 'CIVIL_MARKETPLACE',
  modules: ['auth', 'users', 'admin', 'tenantadmin'], branding: { brandName: 'RK Technologies' } };

const theme = {
  scopeKey: 'PLATFORM', mode: 'light', primaryColor: '#1e40af', accentColor: null, surfaceColor: null, sidebarColor: null,
  borderRadius: 12, fontFamily: null, brandName: null, logoUrl: null, uiStyle: 'default', buttonStyle: 'solid',
  layoutStyle: 'sidebar-left', density: 'comfortable', version: 1,
};
const item = (key: string, label: string, path: string, icon: string, menuGroup: string, sortOrder: number, exactMatch = false) => ({
  key, label, path, icon, section: 'Platform', menuGroup, sortOrder, exactMatch,
});
const menu = [
  item('admin-overview', 'Dashboard', '/admin', 'Dashboard', 'Overview', 100, true),
  item('admin-tenants', 'Tenants', '/admin/tenants', 'Domain', 'Tenants', 110, true),
  item('admin-users', 'Platform staff', '/admin/users', 'Badge', 'Staff', 130),
  item('tenant-overview', 'Tenant dashboard', '/admin/tenant', 'SpaceDashboard', 'Tenant workspace', 300, true),
  item('tenant-users', 'Users', '/admin/tenant/users', 'People', 'Tenant workspace', 301),
  item('tenant-bookings', 'Bookings', '/admin/tenant/bookings', 'BookOnline', 'Tenant workspace', 306),
];
const snapshot = (role: string) => ({ userId: 1, role, menu, theme, timezone: null, dateFormat: null, landingPath: null });

const tenantRow = (tenantKey: string, name: string) => ({
  tenantKey, name, subdomain: tenantKey, customDomain: null, status: 'ACTIVE', contactEmail: `ops@${tenantKey}.in`,
  plan: 'enterprise', vertical: 'CIVIL_MARKETPLACE', modules: ['auth', 'users', 'admin', 'bookings', 'procurement'],
  menuOverrides: [], landingPath: null, branding: null, createdAt: '2026-09-26T10:00:00',
});

const tenantUser = { id: 5, name: 'Deepak Supplier', email: 'supplier@civileng.test', phone: '+919000000010',
  role: 'MATERIAL_SUPPLIER', status: 'ACTIVE', city: 'Hyderabad', emailVerified: true, phoneVerified: true,
  bookings: 0, rating: 0, joinedAt: '2026-09-26T10:00:00' };
const platformStaff = { ...tenantUser, id: 2, name: 'Platform Admin', email: 'platform-admin@rktech.test', role: 'PLATFORM_ADMIN' };

const setUp = async (page: import('@playwright/test').Page, role: string) => {
  await signInAs(page, { id: 1, name: 'RK Staff', email: 'staff@rktech.test', role });
  await mockApi(page, '/tenant-resolution/current', workspace);
  await mockApi(page, /\/api\/v1\/ui-config\/me(\?.*)?$/, snapshot(role));
  await mockApi(page, '/tenants', [tenantRow('platform', 'RK Technologies'), tenantRow('civengmarket', 'CivEngMarket')]);
  await mockApi(page, '/tenants/civengmarket', tenantRow('civengmarket', 'CivEngMarket'));
  // The same endpoint answers for whichever tenant the gateway puts the request on.
  await mockApi(page, /\/api\/v1\/admin\/users(\?.*)?$/, (route: import('@playwright/test').Route) => {
    const acting = route.request().headers()['x-acting-tenant'];
    return json(route, { success: true, data: acting === 'civengmarket' ? [tenantUser] : [platformStaff], totalElements: 1 });
  });
};

test.describe('Platform console: working inside a tenant', () => {
  test('the owner opens CivEngMarket from Tenants and sees its users, not the platform staff', async ({ page }) => {
    await setUp(page, 'PLATFORM_OWNER');
    const shellCalls: Array<{ url: string; acting?: string }> = [];
    page.on('request', (r) => {
      if (/\/api\/v1\/(ui-config|notifications)/.test(r.url())) shellCalls.push({ url: r.url(), acting: r.headers()['x-acting-tenant'] });
    });

    await page.goto('/admin/tenants');
    await page.getByRole('row', { name: /CivEngMarket/ }).getByText('Configure').click();
    await page.getByTestId('open-tenant-workspace').click();

    await expect(page).toHaveURL(/\/admin\/tenant$/);
    await expect(page.getByTestId('acting-tenant-name')).toHaveText('Working in CivEngMarket');

    const usersCall = page.waitForRequest(/\/api\/v1\/admin\/users(\?|$)/);
    await page.getByRole('link', { name: 'Users' }).or(page.getByRole('button', { name: 'Users' })).first().click();
    await expect(page).toHaveURL(/\/admin\/tenant\/users$/);
    expect((await usersCall).headers()['x-acting-tenant']).toBe('civengmarket');
    await expect(page.getByText('Deepak Supplier')).toBeVisible();
    await expect(page.getByText('platform-admin@rktech.test')).toHaveCount(0);

    // The console's own shell never switches tenant.
    expect(shellCalls.length).toBeGreaterThan(0);
    expect(shellCalls.every((c) => c.acting === undefined)).toBe(true);

    // Back on the console's own staff screen, the platform's people again.
    await page.getByRole('link', { name: 'Platform staff' }).or(page.getByRole('button', { name: 'Platform staff' })).first().click();
    await expect(page.getByText('platform-admin@rktech.test')).toBeVisible();
    await expect(page.getByText('Deepak Supplier')).toHaveCount(0);
  });

  test('platform support is told the tenant is read-only for them', async ({ page }) => {
    await setUp(page, 'PLATFORM_SUPPORT');
    await page.addInitScript(() => sessionStorage.setItem('platform.actingTenant',
      JSON.stringify({ tenantKey: 'civengmarket', name: 'CivEngMarket' })));
    await page.goto('/admin/tenant/users');
    await expect(page.getByTestId('acting-tenant-banner')).toContainText('you can look, not change');
    await expect(page.getByText('Deepak Supplier')).toBeVisible();
  });

  test('a tenant owner cannot reach the tenant workspace', async ({ page }) => {
    await signInAs(page, { id: 2, name: 'Civ Owner', email: 'owner@civengmarket.test', role: 'TENANT_OWNER' });
    await mockApi(page, '/tenant-resolution/current', { ...workspace, tenantKey: 'civengmarket', name: 'CivEngMarket',
      modules: ['auth', 'users', 'admin', 'bookings'] });
    await mockApi(page, /\/api\/v1\/ui-config\/me(\?.*)?$/, snapshot('TENANT_OWNER'));
    await page.goto('/admin/tenant/users');
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByTestId('acting-tenant-banner')).toHaveCount(0);
  });

  test('the platform limits a menu item to chosen roles in a tenant', async ({ page }) => {
    await setUp(page, 'PLATFORM_OWNER');
    await mockApi(page, '/admin/menu-catalogue', [
      { itemKey: 'bookings', label: 'Bookings', path: '/bookings', icon: 'BookOnline', section: 'Main', menuGroup: null,
        sortOrder: 10, requiredModule: 'bookings', defaultRoles: '*' },
      { itemKey: 'procurement', label: 'Procurement', path: '/procurement', icon: 'Inventory', section: 'Main', menuGroup: null,
        sortOrder: 20, requiredModule: 'procurement', defaultRoles: 'MATERIAL_SUPPLIER,CIVIL_ENGINEER,CUSTOMER' },
    ]);
    let saved: { menuOverrides: Array<{ itemKey: string; roles?: string | null }> } | null = null;
    await mockApi(page, '/tenants/civengmarket/navigation', (route: import('@playwright/test').Route) => {
      saved = route.request().postDataJSON();
      return json(route, tenantRow('civengmarket', 'CivEngMarket'));
    });

    await page.goto('/admin/tenants');
    await page.getByRole('row', { name: /CivEngMarket/ }).getByText('Configure').click();

    const roles = page.getByTestId('nav-roles-procurement');
    await expect(roles).toBeAttached();
    await page.getByRole('combobox', { name: 'Roles for Procurement' }).click();
    await page.getByRole('option', { name: 'Material supplier' }).click();
    await page.getByRole('option', { name: 'Civil engineer' }).click();
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: 'Save navigation' }).click();
    await expect.poll(() => saved).not.toBeNull();
    const procurement = saved!.menuOverrides.find((o) => o.itemKey === 'procurement');
    expect(procurement?.roles).toBe('MATERIAL_SUPPLIER,CIVIL_ENGINEER');
  });
});
