import { test, expect, mockApi, json, signInAs } from './fixtures';

/**
 * Creating or editing a tenant shows everything it can have: every feature module, the chat and
 * assistant widgets, and a provider account for payments, email, SMS, WhatsApp and the AI assistant
 * (Gemini, OpenAI, Anthropic or an open model on any OpenAI-compatible server).
 */

const owner = { id: 1, name: 'Owner', email: 'owner@platform.test', role: 'PLATFORM_OWNER' };
const rk = { tenantKey: 'platform', name: 'RK Technologies', status: 'ACTIVE', vertical: 'CIVIL_MARKETPLACE',
  modules: ['auth', 'users', 'admin', 'tenantadmin'], branding: { brandName: 'RK Technologies' } };

const catalog = [
  { capability: 'payment', providers: { razorpay: { settings: ['keyId'], secrets: ['keySecret', 'webhookSecret'], optionalSettings: [], optionalSecrets: [] } } },
  { capability: 'email', providers: { brevo: { settings: ['fromAddress', 'fromName'], secrets: ['apiKey'], optionalSettings: [], optionalSecrets: [] } } },
  { capability: 'sms', providers: { twilio: { settings: ['accountSid', 'fromNumber', 'senderId'], secrets: ['authToken'], optionalSettings: [], optionalSecrets: [] } } },
  { capability: 'whatsapp', providers: { twilio: { settings: ['accountSid', 'fromNumber', 'senderName'], secrets: ['authToken'], optionalSettings: [], optionalSecrets: [] } } },
  { capability: 'ai', providers: {
    gemini: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'], optionalSecrets: [] },
    openai: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'], optionalSecrets: [] },
    anthropic: { settings: [], secrets: ['apiKey'], optionalSettings: ['model'], optionalSecrets: [] },
    openai_compatible: { settings: ['baseUrl', 'model'], secrets: [], optionalSettings: [], optionalSecrets: ['apiKey'] },
  } },
];
const plans = { plans: [{ key: 'enterprise', version: 2, name: 'Enterprise',
  features: ['bookings', 'projects', 'reviews', 'search', 'procurement', 'residents', 'feeplans', 'invoices', 'collections',
    'properties', 'listings', 'leases', 'valuations', 'landrecords'], limits: {} }],
  addOns: [], limits: {}, baseModules: ['auth', 'users', 'payments', 'notifications', 'support', 'admin', 'audit', 'messaging'] };

const tenantRow = (modules: string[]) => ({
  tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status: 'DRAFT', contactEmail: 'ops@acme.in',
  plan: 'enterprise', vertical: 'CIVIL_MARKETPLACE', modules, menuOverrides: [], landingPath: null, branding: null,
  createdAt: '2026-09-26T10:00:00',
});

test('the wizard shows every feature, the widgets and every provider — including open AI models', async ({ page }) => {
  const drafts: Record<string, any>[] = [];
  const integrationSaves: Record<string, any> = {};
  await signInAs(page, owner);
  await mockApi(page, '/tenant-resolution/current', rk);
  await mockApi(page, '/tenants', []);
  await mockApi(page, '/tenants/plans', plans);
  await mockApi(page, '/tenants/integration-catalog', catalog);
  await mockApi(page, '/tenants/drafts', (route) => {
    if (route.request().method() === 'POST') {
      drafts.push(route.request().postDataJSON());
      return json(route, { id: 7, title: 'Acme', data: drafts.at(-1), version: 0, status: 'OPEN', tenantKey: null,
        updatedBy: '1', updatedAt: '2026-09-26T10:00:00', issues: [] }, 201);
    }
    return json(route, []);
  });
  await mockApi(page, '/tenants/drafts/7', (route) => {
    drafts.push(route.request().postDataJSON().data);
    return json(route, { id: 7, title: 'Acme', data: drafts.at(-1), version: drafts.length, status: 'OPEN', tenantKey: null,
      updatedBy: '1', updatedAt: '2026-09-26T10:00:01', issues: [] });
  });
  await mockApi(page, '/tenants/drafts/7/create', (route) => json(route, tenantRow(drafts.at(-1)?.modules ?? []), 201));
  await mockApi(page, /\/api\/v1\/tenants\/acme\/integrations\/\w+$/, (route) => {
    const capability = route.request().url().split('/').pop()!;
    integrationSaves[capability] = route.request().postDataJSON();
    return json(route, { capability, configured: true });
  });

  await page.goto('/admin/tenants/new');
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Name', { exact: true }).fill('Acme Builders');
  await dialog.getByLabel('Contact email').fill('ops@acme.in');
  await dialog.getByRole('tab', { name: 'Owner' }).click();
  await dialog.getByLabel("Owner's email").fill('asha@acme.in');

  // Every feature, grouped — this product's first, then the shared services and the other products.
  await dialog.getByRole('tab', { name: 'Features' }).click();
  for (const group of ['core', 'shared', 'marketplace', 'fees', 'property']) {
    await expect(dialog.getByTestId(`feature-group-${group}`)).toBeVisible();
  }
  await expect(dialog.getByLabel('Procurement (B2B)')).toBeChecked();
  await expect(dialog.getByLabel('Auth')).toBeDisabled();
  await dialog.getByLabel('Fee plans').check();

  // Widgets and provider accounts.
  await dialog.getByRole('tab', { name: 'Integrations & widgets' }).click();
  const step = dialog.getByTestId('integrations-step');
  for (const capability of ['payment', 'email', 'sms', 'whatsapp', 'ai']) {
    await expect(step.getByTestId(`integration-${capability}`)).toBeVisible();
  }
  await step.getByLabel('In-app messaging').uncheck();

  // AI: an open model on Groq, no key needed to save the form.
  await step.getByTestId('integration-ai').click();
  const ai = step.getByTestId('integration-ai');
  await ai.getByLabel('Use our own account').check();
  await ai.getByLabel('Provider').click();
  await page.getByRole('option', { name: 'Open model (OpenAI-compatible)' }).click();
  await ai.getByLabel('Start from a known server').click();
  await page.getByRole('option', { name: 'Groq' }).click();
  await expect(ai.getByTestId('ai-setting-baseUrl')).toHaveValue('https://api.groq.com/openai/v1');

  // SMS switched off entirely.
  await step.getByTestId('integration-sms').click();
  await step.getByTestId('integration-sms').getByRole('checkbox', { name: 'On' }).uncheck();
  await expect(step.getByTestId('integration-summary-sms')).toHaveText('Off');

  // Payments on the tenant's own Razorpay account; the secret never reaches the draft.
  await step.getByTestId('integration-payment').click();
  const pay = step.getByTestId('integration-payment');
  await pay.getByLabel('Use our own account').check();
  await pay.getByTestId('payment-setting-keyId').fill('rzp_live_ACME');
  await pay.getByTestId('payment-secret-keySecret').fill('super-secret-key');
  await pay.getByTestId('payment-secret-webhookSecret').fill('super-secret-hook');

  await dialog.getByRole('tab', { name: 'Review' }).click();
  await expect(dialog.getByTestId('wizard-review')).toContainText('AI assistant: Own account · Open model (OpenAI-compatible)');
  await expect(dialog.getByTestId('wizard-review')).toContainText('Messaging off');
  await expect.poll(() => JSON.stringify(drafts.at(-1) ?? {})).toContain('rzp_live_ACME');
  expect(JSON.stringify(drafts)).not.toContain('super-secret');

  await dialog.getByRole('button', { name: 'Create as draft' }).click();
  await expect.poll(() => Object.keys(integrationSaves).sort()).toEqual(['ai', 'payment', 'sms']);
  expect(integrationSaves.ai).toMatchObject({ mode: 'BYO', provider: 'openai_compatible',
    settings: { baseUrl: 'https://api.groq.com/openai/v1' } });
  expect(integrationSaves.payment).toMatchObject({ mode: 'BYO', provider: 'razorpay',
    secrets: { keySecret: 'super-secret-key', webhookSecret: 'super-secret-hook' } });
  expect(integrationSaves.sms).toMatchObject({ enabled: false });
  const modules: string[] = drafts.at(-1)!.modules;
  expect(modules).toEqual(expect.arrayContaining(['auth', 'feeplans', 'support', 'procurement']));
  expect(modules).not.toContain('messaging');
});

test('a tenant\'s page switches its chat widgets', async ({ page }) => {
  let modulesSaved: string[] | null = null;
  let aiSaved: Record<string, unknown> | null = null;
  let tenant = { ...tenantRow(['auth', 'users', 'admin', 'audit', 'payments', 'notifications', 'support', 'messaging', 'bookings']), status: 'ACTIVE' };
  await signInAs(page, owner);
  await mockApi(page, '/tenant-resolution/current', rk);
  await mockApi(page, '/tenants', (route) => json(route, [tenant]));
  await mockApi(page, '/tenants/acme', (route) => json(route, tenant));
  await mockApi(page, '/tenants/integration-catalog', catalog);
  await mockApi(page, '/tenants/acme/integrations', (route) => json(route, aiSaved
    ? [{ capability: 'ai', configured: true, mode: 'PLATFORM_SHARED', provider: null, enabled: false, settings: {},
      secretHints: {}, webhookPath: null, updatedBy: '1', updatedAt: null }] : []));
  await mockApi(page, '/tenants/acme/modules', (route) => {
    modulesSaved = route.request().postDataJSON().modules;
    tenant = { ...tenant, modules: modulesSaved! };
    return json(route, tenant);
  });
  await mockApi(page, '/tenants/acme/integrations/ai', (route) => {
    aiSaved = route.request().postDataJSON();
    return json(route, { capability: 'ai', configured: true, enabled: false });
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  const widgets = page.getByTestId('tenant-widgets');
  await expect(widgets.getByLabel('Support chat widget')).toBeChecked();
  await expect(widgets.getByLabel('AI assistant')).toBeChecked();

  await widgets.getByLabel('In-app messaging').uncheck();
  await expect.poll(() => modulesSaved).not.toBeNull();
  expect(modulesSaved).not.toContain('messaging');

  await widgets.getByLabel('AI assistant').uncheck();
  await expect.poll(() => aiSaved).toMatchObject({ enabled: false, mode: 'PLATFORM_SHARED' });
});
