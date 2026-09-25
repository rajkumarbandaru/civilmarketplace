import { test, expect, mockApi, json, signInAs } from './fixtures';

const superAdmin = { id: 1, name: 'Operator', email: 'ops@example.com', role: 'SUPER_ADMIN' };

const tenant = {
  tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status: 'ACTIVE',
  contactEmail: 'ops@acme.in', plan: null, vertical: 'CIVIL_MARKETPLACE', modules: [], menuOverrides: [],
  landingPath: null, branding: null, createdAt: '2026-09-01T10:00:00',
};

const catalog = [
  { capability: 'payment',
    providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret', 'webhookSecret'] } } },
  { capability: 'email',
    providers: { brevo: { settings: ['fromAddress', 'fromName'], secrets: ['apiKey'] } } },
];

const none = (capability: string) => ({ capability, configured: false, mode: null, provider: null, enabled: false,
  settings: {}, secretHints: {}, webhookPath: null, updatedBy: null, updatedAt: null });

test.describe('Tenant integrations (Super Admin)', () => {
  test('sets up a tenant\'s own Razorpay account; secrets are sent once and only shown masked', async ({ page }) => {
    let payment: Record<string, unknown> = none('payment');
    let saved: Record<string, unknown> | null = null;

    await signInAs(page, superAdmin);
    await mockApi(page, '/tenants', [tenant]);
    await mockApi(page, '/tenants/acme', tenant);
    await mockApi(page, '/tenants/integration-catalog', catalog);
    await mockApi(page, '/tenants/acme/integrations', (route) =>
      json(route, [payment, { ...none('email'), configured: true, mode: 'PLATFORM_SHARED', enabled: true }]));
    await mockApi(page, '/tenants/acme/integrations/payment', (route) => {
      saved = route.request().postDataJSON();
      payment = { ...none('payment'), configured: true, mode: 'BYO', provider: 'razorpay', enabled: true,
        settings: { keyId: 'rzp_live_ACME' }, secretHints: { keySecret: '••••9f2a', webhookSecret: '••••' },
        webhookPath: '/webhooks/payments/razorpay/tok123' };
      return json(route, payment);
    });

    await page.goto('/admin/tenants');
    await page.getByRole('button', { name: 'Configure' }).click();

    const row = page.getByTestId('integration-payment');
    await expect(row.getByText('Platform default')).toBeVisible();
    await expect(page.getByTestId('integration-email').getByText('Platform account')).toBeVisible();

    await row.getByRole('button', { name: 'Change' }).click();
    const dialog = page.getByRole('dialog');
    // Every tenant starts on the platform's account; the form opens there.
    await expect(dialog.getByLabel(/Use the platform's account/)).toBeChecked();
    await dialog.getByLabel('Use our own account').check();
    await dialog.getByRole('button', { name: 'Save' }).click();
    await expect(dialog.getByText('Required')).toHaveCount(3);

    await dialog.getByLabel(/Key ID/).fill('rzp_live_ACME');
    await dialog.getByLabel(/Key secret/).fill('super-secret-9f2a');
    await dialog.getByLabel(/Webhook secret/).fill('whsec');
    await dialog.getByRole('button', { name: 'Save' }).click();
    await expect(dialog).toBeHidden();

    expect(saved).toEqual({ mode: 'BYO', provider: 'razorpay', enabled: true,
      settings: { keyId: 'rzp_live_ACME' }, secrets: { keySecret: 'super-secret-9f2a', webhookSecret: 'whsec' } });
    await expect(row.getByText('Own account · Razorpay')).toBeVisible();
    await expect(row.getByText(/Key secret: ••••9f2a/)).toBeVisible();
    await expect(row.getByText(/webhooks\/payments\/razorpay\/tok123/)).toBeVisible();
    await expect(page.getByText('super-secret-9f2a')).toHaveCount(0);

    // Editing again: the secret box is empty and saving without typing keeps the stored one.
    await row.getByRole('button', { name: 'Change' }).click();
    await expect(dialog.getByLabel(/Key secret/)).toHaveValue('');
    await dialog.getByRole('button', { name: 'Save' }).click();
    await expect(dialog).toBeHidden();
    expect(saved).toEqual({ mode: 'BYO', provider: 'razorpay', enabled: true,
      settings: { keyId: 'rzp_live_ACME' }, secrets: {} });
  });

  test('a 403 from the server is shown, not swallowed', async ({ page }) => {
    await signInAs(page, superAdmin);
    await mockApi(page, '/tenants', [tenant]);
    await mockApi(page, '/tenants/acme', tenant);
    await mockApi(page, '/tenants/integration-catalog', (route) =>
      json(route, { message: "Tenant integrations are restricted to the operator tenant's SUPER_ADMINs" }, 403));
    await mockApi(page, '/tenants/acme/integrations', [none('payment')]);

    await page.goto('/admin/tenants');
    await page.getByRole('button', { name: 'Configure' }).click();
    await expect(page.getByTestId('tenant-integrations').getByText(/restricted to the operator tenant/)).toBeVisible();
  });
});
