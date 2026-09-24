import { test, expect, mockApi, json, signInAs } from './fixtures';

/** Platform Factory: wizard drafts, publish with live provisioning, and the owner's invitation. */

const operator = { id: 1, name: 'Operator', email: 'ops@platform.example', role: 'SUPER_ADMIN' };
const services = ['auth-service', 'user-service', 'admin-service'];

const tenantRow = (status: string) => ({
  tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status,
  contactEmail: 'ops@acme.in', ownerName: 'Asha Rao', ownerEmail: 'asha@acme.in', plan: 'STANDARD',
  vertical: 'CIVIL_MARKETPLACE', modules: ['auth', 'bookings'], menuOverrides: [], landingPath: null, branding: null,
  createdAt: '2026-09-24T10:00:00',
});

const progress = (status: string, step: string | null, ready: number, lastError: string | null = null) => ({
  tenantKey: 'acme', status, step, attempts: 0, lastError, requestedAt: '2026-09-24T10:00:00', finishedAt: null,
  services: services.map((s, i) => ({ service: s, state: i < ready ? 'READY' : 'WAITING', error: null })),
});

test('wizard autosaves a draft, then creates and publishes it, following provisioning to live', async ({ page }) => {
  let saved: Record<string, any> | null = null;
  let version = 0;
  let status = 'DRAFT';
  let polls = 0;
  const calls: string[] = [];

  await signInAs(page, operator);
  await mockApi(page, '/tenants', (route) => json(route, status === 'DRAFT' && !calls.includes('create') ? [] : [tenantRow(status)]));
  await mockApi(page, '/tenants/drafts', (route) => {
    if (route.request().method() === 'POST') {
      calls.push('draft-create');
      saved = route.request().postDataJSON();
      return json(route, { id: 7, title: saved!.name, data: saved, version, status: 'OPEN', tenantKey: null,
        updatedBy: '1', updatedAt: '2026-09-24T10:00:00', issues: [] }, 201);
    }
    return json(route, []);
  });
  await mockApi(page, '/tenants/drafts/7', (route) => {
    const body = route.request().postDataJSON();
    expect(body.version).toBe(version);
    version += 1;
    saved = body.data;
    calls.push('draft-save');
    return json(route, { id: 7, title: saved!.name, data: saved, version, status: 'OPEN', tenantKey: null,
      updatedBy: '1', updatedAt: '2026-09-24T10:00:01', issues: [] });
  });
  await mockApi(page, '/tenants/drafts/7/create', (route) => { calls.push('create'); return json(route, tenantRow('DRAFT'), 201); });
  await mockApi(page, '/tenants/acme/publish', (route) => {
    calls.push('publish');
    status = 'PROVISIONING';
    return json(route, progress('PROVISIONING', 'AWAIT_SCHEMAS', 0), 202);
  });
  await mockApi(page, '/tenants/acme', (route) => json(route, tenantRow(status)));
  await mockApi(page, '/tenants/acme/provisioning', (route) => {
    polls += 1;
    if (status === 'DRAFT') return json(route, progress('DRAFT', null, 0));
    if (polls < 3) return json(route, progress('PROVISIONING', 'AWAIT_SCHEMAS', polls));
    status = 'ACTIVE';
    return json(route, { ...progress('ACTIVE', 'DONE', 3), finishedAt: '2026-09-24T10:00:40' });
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'New tenant' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Name', { exact: true }).fill('Acme Builders');
  await dialog.getByLabel('Contact email').fill('ops@acme.in');
  await expect(dialog.getByTestId('draft-save-state')).toContainText('Draft saved (#7)');
  expect(calls[0]).toBe('draft-create');

  await dialog.getByRole('tab', { name: 'Owner' }).click();
  await dialog.getByLabel("Owner's name").fill('Asha Rao');
  await dialog.getByLabel("Owner's email").fill('asha@acme.in');
  await expect.poll(() => saved?.ownerEmail).toBe('asha@acme.in');
  expect(saved!.form.ownerName).toBe('Asha Rao');
  expect(saved!.tenantKey).toBe('acmebuilders');

  await dialog.getByRole('tab', { name: 'Review' }).click();
  const review = dialog.getByTestId('wizard-review');
  await expect(review).toContainText('Asha Rao <asha@acme.in>');
  await expect(review).toContainText('Ready.');
  await dialog.getByRole('button', { name: 'Create and publish' }).click();

  await expect(page.getByTestId('publish-card')).toBeVisible();
  expect(calls.slice(-2)).toEqual(['create', 'publish']);
  await expect(page.getByTestId('provisioning-progress')).toContainText('of 3 services ready');
  await expect(page.getByTestId('publish-card')).toContainText('asha@acme.in has been sent a link', { timeout: 15_000 });
});

test('an unfinished draft can be resumed exactly as it was left, or discarded', async ({ page }) => {
  let discarded = false;
  const draft = { id: 9, title: 'Bhoomi Homes', version: 4, status: 'OPEN', tenantKey: null, updatedBy: '1',
    updatedAt: '2026-09-23T18:00:00', issues: [{ section: 'owner', message: "Enter the owner's email" }],
    data: { name: 'Bhoomi Homes', form: { name: 'Bhoomi Homes', keyOverride: 'bhoomi', contactEmail: 'hi@bhoomi.in',
      customDomain: '', vertical: 'PROPERTY', branding: {}, modules: ['auth'], overrides: [], landingPath: null,
      ownerName: '', ownerEmail: '' } } };
  await signInAs(page, operator);
  await mockApi(page, '/tenants', []);
  await mockApi(page, '/tenants/drafts', (route) => json(route, discarded ? [] : [draft]));
  await mockApi(page, '/tenants/drafts/9', (route) => {
    if (route.request().method() === 'DELETE') { discarded = true; return route.fulfill({ status: 204 }); }
    return json(route, { ...draft, version: 5 });
  });

  await page.goto('/admin/tenants');
  await expect(page.getByTestId('draft-9')).toContainText('1 thing to finish');
  await page.getByTestId('draft-9').getByRole('button', { name: 'Continue' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Name', { exact: true })).toHaveValue('Bhoomi Homes');
  await expect(dialog.getByLabel('Tenant key')).toHaveValue('bhoomi');
  await expect(dialog.getByTestId('draft-save-state')).toContainText('#9');
  await dialog.getByRole('tab', { name: 'Review' }).click();
  await expect(dialog.getByTestId('review-issues')).toContainText("Enter the owner's email");
  await expect(dialog.getByRole('button', { name: 'Create and publish' })).toBeDisabled();
  await dialog.getByRole('button', { name: 'Cancel' }).click();

  await page.getByTestId('draft-9').getByRole('button', { name: 'Discard' }).click();
  await expect(page.getByTestId('tenant-drafts')).toHaveCount(0);
});

test('a failed publish says which service and why, and can be retried', async ({ page }) => {
  let status = 'PROVISIONING_FAILED';
  await signInAs(page, operator);
  await mockApi(page, '/tenants', (route) => json(route, [tenantRow(status)]));
  await mockApi(page, '/tenants/drafts', []);
  await mockApi(page, '/tenants/acme', (route) => json(route, tenantRow(status)));
  await mockApi(page, '/tenants/acme/provisioning', (route) => json(route, status === 'PROVISIONING_FAILED'
    ? { ...progress('PROVISIONING_FAILED', 'FAILED', 2, 'Timed out waiting for admin-service'),
        services: [...progress('X', null, 2).services.slice(0, 2), { service: 'admin-service', state: 'WAITING', error: null }] }
    : progress('PROVISIONING', 'AWAIT_SCHEMAS', 0)));
  await mockApi(page, '/tenants/acme/publish', (route) => { status = 'PROVISIONING'; return json(route, progress('PROVISIONING', 'AWAIT_SCHEMAS', 0), 202); });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  const card = page.getByTestId('publish-card');
  await expect(card).toContainText('Timed out waiting for admin-service');
  await card.getByRole('button', { name: 'Retry' }).click();
  await expect(card).toContainText('0 of 3 services ready');
});

test.describe('owner invitation', () => {
  test('sets a first password with the link, then points to sign-in', async ({ page }) => {
    let accepted: string | null = null;
    await mockApi(page, '/auth/invitations/tok-123', { email: 'asha@acme.in', name: 'Asha Rao', expiresAt: '2026-09-27T10:00:00' });
    await mockApi(page, '/auth/invitations/tok-123/accept', (route) => {
      accepted = route.request().postDataJSON().password;
      return json(route, { success: true });
    });
    await page.goto('/invite/tok-123');
    const card = page.getByTestId('invite-accept');
    await expect(card).toContainText('asha@acme.in');
    await card.getByLabel('Password', { exact: true }).fill('short');
    await expect(card.getByRole('button', { name: 'Set password' })).toBeDisabled();
    await card.getByLabel('Password', { exact: true }).fill('correct horse battery');
    await card.getByLabel('Confirm password').fill('correct horse batterx');
    await expect(card.getByText('The passwords do not match.')).toBeVisible();
    await card.getByLabel('Confirm password').fill('correct horse battery');
    await card.getByRole('button', { name: 'Set password' }).click();
    await expect(card).toContainText('Your password is set');
    expect(accepted).toBe('correct horse battery');
    await card.getByRole('link', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('a used or expired link says so', async ({ page }) => {
    await mockApi(page, '/auth/invitations/old', (route) =>
      json(route, { success: false, message: 'This invitation link is not valid any more. Ask for a new one.' }, 404));
    await page.goto('/invite/old');
    await expect(page.getByTestId('invite-accept')).toContainText('not valid any more');
  });
});
