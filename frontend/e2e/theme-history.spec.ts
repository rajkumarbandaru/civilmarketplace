import { test, expect, mockApi, json, signInAs } from './fixtures';

/** Theme version history on Admin → Theme: every save is a release; any release can be made live again. */

const superAdmin = { id: 1, name: 'Owner', email: 'owner@example.com', role: 'SUPER_ADMIN' };

const theme = (primaryColor: string, version: number) => ({
  scopeKey: 'PLATFORM', mode: 'light', primaryColor, accentColor: null, surfaceColor: null, sidebarColor: null,
  borderRadius: 12, fontFamily: null, brandName: 'Acme', logoUrl: null, uiStyle: 'default', buttonStyle: 'solid',
  layoutStyle: 'sidebar-left', density: 'comfortable', version,
});

const release = (id: number, source: string, live: boolean, note: string, documents = ['theme']) => ({
  id, scope: 'TENANT', source, changeNote: note, rollbackOfReleaseId: source === 'ROLLBACK' ? 11 : null,
  createdBy: 1, createdAt: '2026-09-24T10:0' + id % 10 + ':00', documents, live,
});

test('shows what each save changed and rolls back to an earlier one', async ({ page }) => {
  let current = theme('#0b8043', 12);
  let releases = [release(12, 'CONSOLE', true, 'Saved in the theme editor'),
    release(11, 'CONSOLE', false, 'Saved in the theme editor', ['branding', 'theme'])];
  let rollbackBody: Record<string, unknown> | null = null;

  await signInAs(page, superAdmin);
  await mockApi(page, '/admin/theme', (route) => json(route, current));
  await mockApi(page, '/admin/theme/presets', []);
  await mockApi(page, '/admin/workspaces', []);
  await mockApi(page, /\/api\/v1\/admin\/config\/releases\?/, (route) => json(route, releases));
  await mockApi(page, '/admin/config/releases/11/diff', { release: releases[1], changes: [
    { document: 'branding', key: 'brandName', before: null, after: 'Acme' },
    { document: 'theme', key: 'primaryColor', before: '#9e9e9e', after: '#1a73e8' },
  ] });
  await mockApi(page, '/admin/config/releases/11/rollback', (route) => {
    rollbackBody = route.request().postDataJSON();
    current = theme('#1a73e8', 13);
    const back = release(13, 'ROLLBACK', true, 'Rolled back to release #11: brand review');
    releases = [back, { ...releases[0], live: false }, releases[1]];
    return json(route, back);
  });

  await page.goto('/admin/theme');
  const history = page.getByTestId('theme-history');
  await expect(history.getByTestId('release-12').getByText('Live')).toBeVisible();

  await history.getByTestId('release-11').click();
  const diff = page.getByTestId('release-diff-11');
  await expect(diff.getByRole('row', { name: /Primary colour/ })).toContainText('#9e9e9e');
  await expect(diff.getByRole('row', { name: /Primary colour/ })).toContainText('#1a73e8');
  await expect(diff.getByRole('row', { name: /Brand name/ })).toContainText('inherited');
  await expect(history.getByTestId('release-12').getByRole('button', { name: 'Roll back to this version' })).toHaveCount(0);

  await history.getByTestId('release-11').getByRole('button', { name: 'Roll back to this version' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Reason (optional)').fill('brand review');
  await dialog.getByRole('button', { name: 'Roll back' }).click();
  await expect(dialog).toBeHidden();

  expect(rollbackBody).toEqual({ reason: 'brand review' });
  await expect(history.getByTestId('release-13').getByText('Live')).toBeVisible();
  await expect(history.getByTestId('release-13')).toContainText('Rollback');
  await expect(page.getByText('Version 13.')).toBeVisible();
});

test('a rollback the server refuses is explained and nothing changes', async ({ page }) => {
  await signInAs(page, superAdmin);
  await mockApi(page, '/admin/theme', theme('#0b8043', 12));
  await mockApi(page, '/admin/theme/presets', []);
  await mockApi(page, '/admin/workspaces', []);
  await mockApi(page, /\/api\/v1\/admin\/config\/releases\?/, [release(12, 'CONSOLE', true, 'x'), release(3, 'SEED', false, 'Imported')]);
  await mockApi(page, '/admin/config/releases/3/diff', { release: {}, changes: [] });
  await mockApi(page, '/admin/config/releases/3/rollback', (route) =>
    json(route, { success: false, message: 'theme.primaryColor must be a hex colour like #1a73e8' }, 400));

  await page.goto('/admin/theme');
  await page.getByTestId('release-3').click();
  await page.getByTestId('release-3').getByRole('button', { name: 'Roll back to this version' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Roll back' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('must be a hex colour');
  await expect(page.getByTestId('release-12').getByText('Live')).toBeVisible();
});
