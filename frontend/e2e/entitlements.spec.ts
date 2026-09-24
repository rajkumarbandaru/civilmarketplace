import { test, expect, mockApi, json, signInAs } from './fixtures';

/** Plans, add-ons, grants: what a tenant may run, previewed before it changes. */

const operator = { id: 1, name: 'Operator', email: 'ops@platform.example', role: 'SUPER_ADMIN' };
const catalog = {
  plans: [
    { key: 'starter', version: 1, name: 'Starter', features: ['bookings', 'reviews', 'search'], limits: { 'bookings.monthly': 500, 'staff.seats': 5 } },
    { key: 'professional', version: 1, name: 'Professional', features: ['bookings', 'reviews', 'search', 'projects'], limits: { 'bookings.monthly': 5000, 'staff.seats': 50 } },
  ],
  addOns: [{ key: 'projects', name: 'Projects module', features: ['projects'], increments: {} }],
  limits: { 'staff.seats': 'Staff users', 'bookings.monthly': 'Bookings per month' },
  baseModules: ['auth', 'users', 'payments', 'notifications', 'support', 'admin', 'audit', 'messaging'],
};
const tenant = {
  tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status: 'ACTIVE',
  contactEmail: 'ops@acme.in', plan: 'professional', vertical: 'CIVIL_MARKETPLACE',
  modules: ['auth', 'users', 'bookings', 'projects', 'reviews', 'search'], menuOverrides: [], landingPath: null,
  branding: null, createdAt: '2026-09-01T10:00:00',
};
const base = catalog.baseModules;
const ent = (plan: 'starter' | 'professional', grants: object[] = []) => {
  const p = catalog.plans.find((x) => x.key === plan)!;
  const features = [...base, ...p.features];
  return {
    entitlements: { tenantKey: 'acme', planKey: plan, planVersion: 1, planName: p.name, status: 'ACTIVE', addOns: [],
      features, limits: p.limits, grants },
    chosenModules: tenant.modules,
    runningModules: tenant.modules.filter((m) => features.includes(m)),
  };
};

test.beforeEach(async ({ page }) => {
  await signInAs(page, operator);
  await mockApi(page, '/tenants', [tenant]);
  await mockApi(page, '/tenants/drafts', []);
  await mockApi(page, '/tenants/acme', tenant);
  await mockApi(page, '/tenants/plans', catalog);
});

test('a downgrade previews exactly what stops, then keeps the choice dormant', async ({ page }) => {
  let plan: 'starter' | 'professional' = 'professional';
  let changedTo: Record<string, unknown> | null = null;
  await mockApi(page, '/tenants/acme/entitlements', (route) => json(route, ent(plan)));
  await mockApi(page, /\/api\/v1\/tenants\/acme\/subscription\/preview/, (route) => {
    const to = new URL(route.request().url()).searchParams.get('plan');
    return json(route, to === 'starter'
      ? { fromPlan: 'Professional', toPlan: 'Starter', modulesStopping: ['projects'], modulesResuming: [],
          limitChanges: { 'bookings.monthly': [5000, 500], 'staff.seats': [50, 5] } }
      : { fromPlan: 'Professional', toPlan: 'Professional', modulesStopping: [], modulesResuming: [], limitChanges: {} });
  });
  await mockApi(page, '/tenants/acme/subscription', (route) => {
    changedTo = route.request().postDataJSON();
    plan = 'starter';
    return json(route, ent('starter').entitlements);
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  const card = page.getByTestId('plan-card');
  await expect(card).toContainText('Professional v1');
  await expect(card.getByTestId('limit-bookings.monthly')).toHaveText('5,000');

  await card.getByRole('button', { name: 'Change plan' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByTestId('plan-impact')).toContainText('No change');
  await dialog.getByLabel('Plan').click();
  await page.getByRole('option', { name: 'Starter (v1)' }).click();
  const impact = dialog.getByTestId('plan-impact');
  await expect(impact).toContainText('Stops running: Projects');
  await expect(impact).toContainText('the data is kept');
  await expect(impact).toContainText('Bookings per month: 5,000 → 500');
  await dialog.getByRole('button', { name: 'Change plan' }).click();

  expect(changedTo).toEqual({ plan: 'starter', addOns: [] });
  await expect(card).toContainText('Starter v1');
  await expect(card.getByTestId('dormant-modules')).toContainText('Projects');
  await expect(page.getByTestId('not-in-plan-projects')).toContainText('chosen, not running');
});

test('a module outside the plan cannot be switched on; a grant can bring it in until a date', async ({ page }) => {
  let grants: object[] = [];
  let posted: Record<string, unknown> | null = null;
  await mockApi(page, '/tenants/acme', { ...tenant, modules: ['auth', 'users', 'bookings', 'reviews'] });
  await mockApi(page, '/tenants/acme/entitlements', (route) => json(route, { ...ent('starter', grants),
    chosenModules: ['auth', 'users', 'bookings', 'reviews'], runningModules: ['auth', 'users', 'bookings', 'reviews'] }));
  await mockApi(page, '/tenants/acme/grants', (route) => {
    posted = route.request().postDataJSON();
    grants = [{ id: 1, feature: 'projects', limitValue: null, expiresAt: '2026-12-31T23:59:00', reason: 'Q4 trial', grantedBy: '1', active: true }];
    return json(route, ent('starter', grants).entitlements);
  });
  await mockApi(page, '/tenants/acme/grants/1', (route) => { grants = []; return json(route, ent('starter').entitlements); });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  await expect(page.getByTestId('not-in-plan-projects')).toHaveText('Not in plan');
  await expect(page.getByRole('checkbox', { name: 'Projects' })).toBeDisabled();

  const card = page.getByTestId('plan-card');
  await card.getByRole('button', { name: 'Add grant' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Feature or limit').click();
  await page.getByRole('option', { name: 'Projects' }).click();
  await dialog.getByLabel('Until').fill('2026-12-31');
  await dialog.getByLabel('Reason').fill('Q4 trial');
  await dialog.getByRole('button', { name: 'Grant' }).click();

  expect(posted).toEqual({ feature: 'projects', limitValue: null, expiresAt: '2026-12-31T23:59:00', reason: 'Q4 trial' });
  await expect(card.getByTestId('grant-1')).toContainText('Q4 trial');
  await card.getByTestId('grant-1').getByRole('button', { name: 'Revoke' }).click();
  await expect(card).toContainText('No grants.');
});

test('the wizard offers plans and flags modules the chosen plan does not include', async ({ page }) => {
  let saved: Record<string, any> | null = null;
  await mockApi(page, '/tenants/drafts', (route) => {
    if (route.request().method() === 'POST') {
      saved = route.request().postDataJSON();
      return json(route, { id: 3, title: 'x', data: saved, version: 0, status: 'OPEN', tenantKey: null, updatedBy: '1',
        updatedAt: '2026-09-24T10:00:00', issues: [] }, 201);
    }
    return json(route, []);
  });
  await mockApi(page, '/tenants/drafts/3', (route) => {
    saved = route.request().postDataJSON().data;
    return json(route, { id: 3, title: 'x', data: saved, version: 1, status: 'OPEN', tenantKey: null, updatedBy: '1',
      updatedAt: '2026-09-24T10:00:01', issues: [] });
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'New tenant' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Name', { exact: true }).fill('Acme Builders');
  await dialog.getByRole('tab', { name: 'Modules' }).click();
  await dialog.getByLabel('Plan').click();
  await page.getByRole('option', { name: 'Starter' }).click();
  await expect(dialog.getByTestId('not-in-plan-projects')).toContainText('chosen, not running');
  await expect.poll(() => saved?.plan).toBe('starter');
  await dialog.getByRole('tab', { name: 'Review' }).click();
  await expect(dialog.getByTestId('wizard-review')).toContainText('1 not in plan — will not run');
});
