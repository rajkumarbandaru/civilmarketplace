import { Page } from '@playwright/test';
import { test, expect, mockApi, json, signInAs, customer, admin } from './fixtures';

/**
 * Every place a file can be uploaded. Storage is mocked at its own origin, the way the browser
 * really reaches it (the signed form goes straight to storage, not through the API).
 */

const STORAGE = 'http://storage.test:9000/civeng-public';
const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]);
const PDF = Buffer.from('%PDF-1.7\n');
const png = (name = 'photo.png') => ({ name, mimeType: 'image/png', buffer: PNG });

interface Uploads { requested: Array<Record<string, unknown>>; stored: number; completed: string[] }

/** Mocks the three upload steps; each upload gets a fresh id and a public URL. */
const mockUploads = async (page: Page, visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC'): Promise<Uploads> => {
  const log: Uploads = { requested: [], stored: 0, completed: [] };
  let n = 0;
  await mockApi(page, '/media/uploads', (route) => {
    const body = route.request().postDataJSON();
    log.requested.push(body);
    n += 1;
    return json(route, { mediaId: `m${n}`, uploadUrl: STORAGE, fields: { key: `k${n}`, policy: 'p' },
      maxBytes: 5_000_000, expiresAt: new Date(Date.now() + 600_000).toISOString() }, 201);
  });
  await page.route(`${STORAGE}**`, (route) => {
    log.stored += 1;
    return route.fulfill({ status: 204, headers: { 'Access-Control-Allow-Origin': '*' } });
  });
  await mockApi(page, /\/api\/v1\/media\/m\d+\/complete$/, (route) => {
    const id = route.request().url().match(/media\/(m\d+)\//)![1];
    log.completed.push(id);
    const req = log.requested[Number(id.slice(1)) - 1];
    return json(route, { id, purpose: req.purpose, visibility, status: 'READY', originalFilename: req.filename,
      contentType: req.contentType, url: `${STORAGE}/k${id.slice(1)}` });
  });
  return log;
};

test.describe('Profile photo', () => {
  test('uploads a photo and shows it straight away', async ({ page }) => {
    await signInAs(page, customer);
    const uploads = await mockUploads(page);
    let savedMediaId = '';
    await mockApi(page, '/auth/me/profile-picture', (route) => {
      savedMediaId = route.request().postDataJSON().mediaId;
      return json(route, { ...customer, profilePicture: `${STORAGE}/k1` });
    });

    await page.goto('/profile');
    await page.getByTestId('upload-input-AVATAR').setInputFiles(png('me.png'));

    await expect(page.locator(`img[src="${STORAGE}/k1"]`).first()).toBeVisible();
    expect(uploads.requested[0]).toEqual({ purpose: 'AVATAR', filename: 'me.png', contentType: 'image/png', sizeBytes: PNG.length });
    expect(uploads.stored).toBe(1);
    expect(savedMediaId).toBe('m1');
  });

  test('a file of the wrong type is refused before anything is sent', async ({ page }) => {
    await signInAs(page, customer);
    const uploads = await mockUploads(page);
    await page.goto('/profile');
    await page.getByTestId('upload-input-AVATAR').setInputFiles({ name: 'x.pdf', mimeType: 'application/pdf', buffer: PDF });
    await expect(page.getByRole('alert')).toContainText('This file type is not allowed');
    expect(uploads.requested).toHaveLength(0);
  });
});

test.describe('KYC documents', () => {
  test('submits an uploaded document and opens it through a short-lived link', async ({ page, context }) => {
    await signInAs(page, customer);
    await mockUploads(page, 'PRIVATE');
    const docs: Array<Record<string, unknown>> = [];
    let submitted: Record<string, unknown> | null = null;
    await mockApi(page, '/users/kyc', (route) => {
      if (route.request().method() === 'POST') {
        submitted = route.request().postDataJSON();
        docs.push({ id: 11, userId: 7, documentType: 'PAN', documentNumber: 'ABCDE1234F', mediaId: 'm1',
          status: 'PENDING', rejectionReason: null, createdAt: '2026-09-24T10:00:00', reviewedAt: null });
        return json(route, docs[0], 201);
      }
      return json(route, docs);
    });
    await mockApi(page, '/users/kyc/11/file', { url: 'http://storage.test:9000/signed/pan.pdf?X-Amz-Expires=300',
      expiresAt: null, filename: 'pan.pdf', contentType: 'application/pdf' });
    await context.route('http://storage.test:9000/signed/**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/pdf', body: PDF }));

    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Verification' }).click();
    const section = page.getByTestId('kyc-section');
    await expect(section.getByText('Nothing submitted yet.')).toBeVisible();

    await section.getByLabel('Document type').click();
    await page.getByRole('option', { name: 'PAN card' }).click();
    await section.getByLabel('Document number (optional)').fill('ABCDE1234F');
    await page.getByTestId('upload-input-KYC_DOCUMENT').setInputFiles({ name: 'pan.pdf', mimeType: 'application/pdf', buffer: PDF });
    await expect(section.getByText('pan.pdf')).toBeVisible();
    await section.getByRole('button', { name: 'Submit for verification' }).click();

    await expect(page.getByTestId('kyc-doc-11')).toContainText('PAN card · ABCDE1234F');
    await expect(page.getByTestId('kyc-doc-11')).toContainText('PENDING');
    expect(submitted).toEqual({ documentType: 'PAN', documentNumber: 'ABCDE1234F', mediaId: 'm1' });

    // Headless Chromium downloads a PDF rather than showing it, so assert the new tab asked for it.
    const opened = context.waitForEvent('request', (r) => r.url().includes('/signed/pan.pdf'));
    await page.getByTestId('kyc-doc-11').getByRole('button', { name: 'View' }).click();
    expect((await opened).url()).toContain('X-Amz-Expires=300');
  });
});

test.describe('Portfolio', () => {
  const plumber = { ...customer, role: 'PLUMBER' };

  test('is offered to people who offer work, not to customers', async ({ page }) => {
    await signInAs(page, customer);
    await page.goto('/profile');
    await expect(page.getByRole('tab', { name: 'Verification' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Portfolio' })).toHaveCount(0);
  });

  test('adds an uploaded photo and removes it', async ({ page }) => {
    await signInAs(page, plumber);
    await mockUploads(page);
    let items: Array<Record<string, unknown>> = [];
    let added: Record<string, unknown> | null = null;
    await mockApi(page, '/users/portfolio', (route) => {
      if (route.request().method() === 'POST') {
        added = route.request().postDataJSON();
        items = [{ id: 4, userId: 7, title: 'Bathroom refit', description: null, imageUrl: `${STORAGE}/k1`,
          category: 'Plumbing', completionDate: null, createdAt: '2026-09-24T10:00:00' }];
        return json(route, items[0], 201);
      }
      return json(route, items);
    });
    await mockApi(page, '/users/portfolio/4', (route) => { items = []; return route.fulfill({ status: 204 }); });
    await page.route(`${STORAGE}/k1`, (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }));

    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Portfolio' }).click();
    const section = page.getByTestId('portfolio-section');
    await expect(section.getByRole('button', { name: 'Add to portfolio' })).toBeDisabled();

    await page.getByTestId('upload-input-PORTFOLIO').setInputFiles(png('bath.png'));
    await expect(section.getByAltText('New portfolio photo')).toBeVisible();
    await section.getByLabel('Title').fill('Bathroom refit');
    await section.getByLabel('Category (optional)').fill('Plumbing');
    await section.getByRole('button', { name: 'Add to portfolio' }).click();

    await expect(page.getByTestId('portfolio-item-4')).toContainText('Bathroom refit');
    expect(added).toEqual({ title: 'Bathroom refit', category: 'Plumbing', mediaId: 'm1' });

    await page.getByRole('button', { name: 'Remove Bathroom refit' }).click();
    await expect(section.getByText('No photos yet.')).toBeVisible();
  });
});

test.describe('Admin KYC review', () => {
  const pendingDoc = { id: 21, userId: 7, documentType: 'AADHAAR', documentNumber: null, mediaId: 'm9',
    status: 'PENDING', rejectionReason: null, createdAt: '2026-09-20T09:00:00', reviewedAt: null };

  test('opens, approves and rejects with a reason', async ({ page, context }) => {
    await signInAs(page, admin);
    let queue = [pendingDoc, { ...pendingDoc, id: 22, documentType: 'GST' }];
    let rejectBody: Record<string, unknown> | null = null;
    await mockApi(page, /\/api\/v1\/users\/admin\/kyc\/pending/, (route) =>
      json(route, { success: true, data: queue, page: 0, size: 20, totalElements: queue.length, totalPages: 1 }));
    await mockApi(page, '/users/admin/kyc/21/file', { url: 'http://storage.test:9000/signed/aadhaar.png', expiresAt: null,
      filename: 'a.png', contentType: 'image/png' });
    await context.route('http://storage.test:9000/signed/**', (route) =>
      route.fulfill({ status: 200, contentType: 'image/png', body: PNG }));
    await mockApi(page, '/users/admin/kyc/21/approve', (route) => {
      queue = queue.filter((d) => d.id !== 21);
      return json(route, { ...pendingDoc, status: 'APPROVED' });
    });
    await mockApi(page, '/users/admin/kyc/22/reject', (route) => {
      rejectBody = route.request().postDataJSON();
      queue = queue.filter((d) => d.id !== 22);
      return json(route, { ...pendingDoc, id: 22, status: 'REJECTED' });
    });

    await page.goto('/admin/kyc');
    await expect(page.getByTestId('kyc-review-21')).toContainText('Aadhaar');

    const popup = context.waitForEvent('page');
    await page.getByTestId('kyc-review-21').getByRole('button', { name: 'View document' }).click();
    await expect(await popup).toHaveURL(/signed\/aadhaar\.png/);

    await page.getByTestId('kyc-review-21').getByRole('button', { name: 'Approve' }).click();
    await expect(page.getByTestId('kyc-review-21')).toHaveCount(0);

    await page.getByTestId('kyc-review-22').getByRole('button', { name: 'Reject' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('button', { name: 'Reject' })).toBeDisabled();
    await dialog.getByLabel('Reason (shown to the member)').fill('Photo is blurred');
    await dialog.getByRole('button', { name: 'Reject' }).click();
    await expect(page.getByText('Nothing waiting for review.')).toBeVisible();
    expect(rejectBody).toEqual({ reason: 'Photo is blurred' });
  });

  test('is in the admin navigation', async ({ page }) => {
    await signInAs(page, admin);
    await mockApi(page, /\/api\/v1\/users\/admin\/kyc\/pending/, { success: true, data: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await page.goto('/admin');
    await page.getByRole('link', { name: 'KYC review' }).or(page.getByRole('button', { name: 'KYC review' })).first().click();
    await expect(page).toHaveURL(/\/admin\/kyc$/);
  });
});

test.describe('Admin image fields', () => {
  test('service media: an uploaded video fills the URL and media type', async ({ page }) => {
    await signInAs(page, admin);
    await mockUploads(page);
    await page.goto('/admin/services');
    await page.getByRole('button', { name: 'Add Service' }).click();
    await page.getByTestId('upload-input-SERVICE_MEDIA').setInputFiles({ name: 'dig.mp4', mimeType: 'video/mp4',
      buffer: Buffer.from([0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6d, 0x70, 0x34, 0x32]) });
    await expect(page.getByLabel('Photo / video / animation URL')).toHaveValue(`${STORAGE}/k1`);
    await expect(page.getByRole('dialog').getByText('Video', { exact: true })).toBeVisible();
  });

  test('theme logo: an uploaded logo fills the Logo URL', async ({ page }) => {
    await signInAs(page, { ...admin, role: 'SUPER_ADMIN' });
    await mockUploads(page);
    await mockApi(page, '/admin/theme', { brandName: 'Civil', logoUrl: null });
    await page.goto('/admin/theme');
    await page.getByTestId('upload-input-BRAND_LOGO').setInputFiles(png('logo.png'));
    await expect(page.getByLabel('Logo URL')).toHaveValue(`${STORAGE}/k1`);
  });

  test('tenant logo: an uploaded logo fills the tenant\'s Logo URL', async ({ page }) => {
    await signInAs(page, { ...admin, role: 'SUPER_ADMIN' });
    const uploads = await mockUploads(page);
    const tenant = { tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status: 'ACTIVE',
      contactEmail: null, plan: null, vertical: 'CIVIL_MARKETPLACE', modules: [], menuOverrides: [], landingPath: null,
      branding: null, createdAt: '2026-09-01T10:00:00' };
    await mockApi(page, '/tenants', [tenant]);
    await mockApi(page, '/tenants/acme', tenant);
    await page.goto('/admin/tenants');
    await page.getByRole('button', { name: 'Configure' }).click();
    await page.getByRole('button', { name: 'Set branding' }).click();
    await page.getByTestId('upload-input-TENANT_LOGO').setInputFiles(png('acme.png'));
    await expect(page.getByRole('dialog').getByLabel('Logo URL')).toHaveValue(`${STORAGE}/k1`);
    expect(uploads.requested[0]).toMatchObject({ purpose: 'TENANT_LOGO' });
  });
});
