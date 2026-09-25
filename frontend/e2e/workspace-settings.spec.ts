import { test, expect, mockApi, json, signInAs } from './fixtures';

/**
 * A workspace's own admins: modules on/off within the plan, their own provider accounts (with the
 * platform's as the default) and adding users with a role. Plus the operator's "start from an
 * existing tenant" in the new-tenant wizard.
 */

const tenantAdmin = { id: 5, name: 'Asha', email: 'asha@acme.in', role: 'ADMIN' };
const operator = { id: 1, name: 'Operator', email: 'ops@platform.example', role: 'SUPER_ADMIN' };

const mod = (key: string, over: Record<string, boolean> = {}) =>
  ({ key, chosen: true, running: true, entitled: true, locked: false, ...over });

const catalog = [
  { capability: 'payment', providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret', 'webhookSecret'], optionalSettings: [] } } },
  { capability: 'ai', providers: {
    gemini: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'] },
    openai: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'] },
    anthropic: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'] },
  } },
];
const none = (capability: string) => ({ capability, configured: false, mode: null, provider: null, enabled: false,
  settings: {}, secretHints: {}, webhookPath: null, updatedBy: null, updatedAt: null });

test('a workspace admin switches modules within the plan and moves the AI assistant to Claude', async ({ page }) => {
  let modules = [mod('auth', { locked: true }), mod('bookings'), mod('projects', { chosen: false, running: false }),
    mod('leases', { chosen: false, running: false, entitled: false })];
  let sentModules: string[] | null = null;
  let ai: Record<string, unknown> = none('ai');
  let savedAi: Record<string, unknown> | null = null;

  await signInAs(page, tenantAdmin);
  await mockApi(page, '/workspace-settings/modules', (route) => {
    if (route.request().method() === 'PUT') {
      sentModules = route.request().postDataJSON().modules;
      modules = modules.map((m) => (m.locked ? m : { ...m, chosen: sentModules!.includes(m.key),
        running: sentModules!.includes(m.key) && m.entitled }));
    }
    return json(route, { tenantKey: 'acme', planName: 'Professional', modules });
  });
  await mockApi(page, '/workspace-settings/integration-catalog', catalog);
  await mockApi(page, '/workspace-settings/integrations', (route) => json(route, [none('payment'), ai]));
  await mockApi(page, '/workspace-settings/integrations/ai', (route) => {
    savedAi = route.request().postDataJSON();
    ai = { ...none('ai'), configured: true, mode: 'BYO', provider: 'anthropic', enabled: true,
      settings: { model: 'claude-sonnet-5' }, secretHints: { apiKey: '••••wxyz' } };
    return json(route, ai);
  });

  await page.goto('/admin/workspace-settings');
  await expect(page.getByText('Professional plan')).toBeVisible();
  await expect(page.getByTestId('module-leases').getByRole('checkbox')).toBeDisabled();
  await expect(page.getByTestId('module-leases')).toContainText('Not in your plan');

  await page.getByTestId('module-projects').getByRole('checkbox').check();
  await expect(page.getByTestId('module-projects').getByText('Running')).toBeVisible();
  expect(sentModules).toEqual(['bookings', 'projects']);

  // Everything starts on the platform's accounts.
  await expect(page.getByTestId('integration-payment').getByText('Platform default')).toBeVisible();
  const aiRow = page.getByTestId('integration-ai');
  await expect(aiRow.getByText('Platform default')).toBeVisible();

  await aiRow.getByRole('button', { name: 'Change' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Use our own account').check();
  await dialog.getByRole('combobox', { name: 'Provider' }).click();
  await page.getByRole('option', { name: 'Anthropic (Claude)' }).click();
  await dialog.getByLabel(/Model \(optional\)/).fill('claude-sonnet-5');
  await dialog.getByLabel(/API key/).fill('sk-ant-secret-wxyz');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(dialog).toBeHidden();

  expect(savedAi).toEqual({ mode: 'BYO', provider: 'anthropic', enabled: true,
    settings: { model: 'claude-sonnet-5' }, secrets: { apiKey: 'sk-ant-secret-wxyz' } });
  await expect(aiRow.getByText('Own account · Anthropic (Claude)')).toBeVisible();
  await expect(page.getByText('sk-ant-secret-wxyz')).toHaveCount(0);
});

test('a workspace admin adds a user with a role, who is invited by email', async ({ page }) => {
  let invited: Record<string, unknown> | null = null;
  await signInAs(page, tenantAdmin);
  await mockApi(page, /\/api\/v1\/admin\/users(\?.*)?$/, (route) => {
    if (route.request().method() === 'POST') {
      invited = route.request().postDataJSON();
      return json(route, { success: true, data: { userId: 9, email: 'ravi@acme.in', name: 'Ravi', role: 'SITE_ENGINEER',
        expiresAt: '2026-09-28T10:00:00' } });
    }
    return json(route, { success: true, data: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });
  await mockApi(page, '/admin/users/roles', { success: true, data: ['ADMIN', 'CUSTOMER', 'SITE_ENGINEER', 'SUPER_ADMIN']
    .map((name) => ({ name, description: '', systemRole: true, userCount: 0 })) });

  await page.goto('/admin/users');
  await page.getByRole('button', { name: 'Add User' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Name').fill('Ravi');
  await dialog.getByLabel(/Email/).fill('ravi@acme.in');
  await dialog.getByTestId('role-select').getByRole('combobox').click();
  // A workspace admin cannot make a Super Admin.
  await expect(page.getByRole('option', { name: 'Super admin' })).toHaveCount(0);
  await page.getByRole('option', { name: 'Site engineer' }).click();
  await dialog.getByRole('button', { name: 'Add and invite' }).click();

  await expect(page.getByText('ravi@acme.in added as Site engineer and invited by email')).toBeVisible();
  expect(invited).toMatchObject({ name: 'Ravi', email: 'ravi@acme.in', role: 'SITE_ENGINEER' });
  expect(String(invited!.linkBase)).toMatch(/^http/);
});

test('the operator starts a new tenant from an existing one', async ({ page }) => {
  const acme = {
    tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status: 'ACTIVE',
    contactEmail: 'ops@acme.in', plan: 'enterprise', vertical: 'CIVIL_MARKETPLACE',
    modules: ['auth', 'users', 'payments', 'bookings', 'procurement'], menuOverrides: [], landingPath: null,
    branding: { brandName: 'Acme', primaryColor: '#123456' }, createdAt: '2026-09-01T10:00:00',
  };
  let draft: Record<string, any> | null = null;
  await signInAs(page, operator);
  await mockApi(page, '/tenants', [acme]);
  await mockApi(page, '/tenants/acme', acme);
  await mockApi(page, '/tenants/drafts', (route) => {
    if (route.request().method() === 'POST') {
      draft = route.request().postDataJSON();
      return json(route, { id: 3, title: draft!.name, data: draft, version: 0, status: 'OPEN', tenantKey: null,
        updatedBy: '1', updatedAt: '2026-09-25T10:00:00', issues: [] }, 201);
    }
    return json(route, []);
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'New tenant' }).click();
  await page.getByTestId('start-from-tenant').getByRole('combobox').click();
  await page.getByRole('option', { name: 'Acme Builders (acme)' }).click();
  await expect(page.getByText("Copied Acme Builders's setup")).toBeVisible();

  await page.getByLabel('Name', { exact: true }).fill('Bharat Infra');
  await expect.poll(() => draft?.name).toBe('Bharat Infra');
  expect(draft!.plan).toBe('enterprise');
  expect(draft!.modules).toEqual(expect.arrayContaining(['bookings', 'procurement']));
  expect(draft!.branding).toMatchObject({ primaryColor: '#123456' });
  expect(draft!.contactEmail).toBe('');
});
