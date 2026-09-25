import { test, expect, mockApi, json, signInAs } from './fixtures';

/** Phase 6: where a tenant's data lives, and moving it between MySQL clusters. */

const operator = { id: 1, name: 'Operator', email: 'ops@platform.example', role: 'SUPER_ADMIN' };
const tenant = {
  tenantKey: 'acme', name: 'Acme Builders', subdomain: 'acme', customDomain: null, status: 'ACTIVE',
  contactEmail: 'ops@acme.in', plan: 'professional', vertical: 'CIVIL_MARKETPLACE',
  modules: ['auth', 'users', 'bookings'], menuOverrides: [], landingPath: null, branding: null, createdAt: '2026-09-01T10:00:00',
};
const clusters = [
  { clusterId: 'cluster-a', cell: 'cell-1', host: 'mysql', port: 3306, kind: 'SHARED', status: 'ACTIVE', capacity: 500, tenants: 9 },
  { clusterId: 'cluster-b', cell: 'cell-1', host: 'mysql-b', port: 3306, kind: 'SHARED', status: 'ACTIVE', capacity: 500, tenants: 0 },
];
const move = (step: string, extra: object = {}) => ({
  id: 4, tenantKey: 'acme', sourceClusterId: 'cluster-a', targetClusterId: 'cluster-b', step, lastError: null,
  schemasCopied: 13, tablesCopied: step === 'COPY' ? 0 : 140, rowsCopied: step === 'COPY' ? 0 : 5230, tablesResynced: 1,
  acknowledged: [], waitingFor: step === 'AWAIT_ACKS' ? ['booking-service'] : [], freezeMillis: step === 'COPY' ? null : 3900,
  report: step === 'DONE' ? { tablesVerified: 140, mismatches: [] } : null, requestedBy: '1',
  startedAt: '2026-09-25T10:00:00', finishedAt: null, sourceDroppedAt: null, ...extra,
});

test('an operator moves a tenant to another cluster and follows it to done', async ({ page }) => {
  await signInAs(page, operator);
  await mockApi(page, '/tenants', [tenant]);
  await mockApi(page, '/tenants/drafts', []);
  await mockApi(page, '/tenants/acme', tenant);
  await mockApi(page, '/tenants/clusters', clusters);
  await mockApi(page, '/tenants/acme/moves', (route) => {
    if (route.request().method() === 'POST') {
      progress = ['COPY', 'AWAIT_ACKS', 'DONE'];
      return json(route, move('COPY'), 202);
    }
    return json(route, []);
  });
  let progress: string[] = [];
  await mockApi(page, '/tenants/acme/placement', (route) => {
    const step = progress.length > 1 ? progress.shift()! : progress[0];
    return json(route, { tenantKey: 'acme', clusterId: step === 'DONE' ? 'cluster-b' : 'cluster-a', cell: 'cell-1',
      tier: 'STANDARD', epoch: step === 'DONE' ? 1 : 0, status: step === 'AWAIT_ACKS' ? 'MAINTENANCE' : 'ACTIVE',
      currentMove: step ? move(step) : null });
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  const card = page.getByTestId('placement-card');
  await expect(card.getByTestId('placement-cluster')).toHaveText('cluster-a');
  await card.getByLabel('Move to').click();
  await page.getByRole('option', { name: /cluster-b/ }).click();
  await card.getByRole('button', { name: 'Move' }).click();
  await expect(page.getByRole('dialog')).toContainText('The old copy is kept on cluster-a');
  await page.getByRole('button', { name: 'Start move' }).click();

  await expect(card.getByText('Writes paused', { exact: true })).toBeVisible();
  await expect(card).toContainText('Waiting for: booking-service');
  await expect(card.getByTestId('placement-cluster')).toHaveText('cluster-b', { timeout: 10000 });
  await expect(card.getByTestId('move-stats-4')).toContainText('140 tables verified · writes paused 3.9 s');
  await expect(card.getByRole('button', { name: 'Drop old copy on cluster-a' })).toBeVisible();
});

test('an operator adds a custom domain and follows it from DNS proof to live', async ({ page }) => {
  await signInAs(page, operator);
  await mockApi(page, '/tenants', [tenant]);
  await mockApi(page, '/tenants/drafts', []);
  await mockApi(page, '/tenants/acme', tenant);
  await mockApi(page, '/tenants/clusters', clusters);
  await mockApi(page, '/tenants/acme/moves', []);
  await mockApi(page, '/tenants/acme/placement', { tenantKey: 'acme', clusterId: 'cluster-a', cell: 'cell-1', tier: 'STANDARD',
    epoch: 0, status: 'ACTIVE', currentMove: null });
  const records = [
    { type: 'TXT', name: '_platform-verify.www.acme-builders.test', value: 'a1b2c3', purpose: 'Proves you control the name' },
    { type: 'CNAME', name: 'www.acme-builders.test', value: 'edge.civilengineer.com', purpose: 'Sends its traffic to the platform' },
  ];
  let stages = [[] as object[]];
  const domain = (status: string, extra: object = {}) => ({ id: 1, tenantKey: 'acme', host: 'www.acme-builders.test',
    surface: 'WEB', status, records, certIssuer: null, certNotAfter: null, attempts: 0, lastError: null, lastCheckedAt: null,
    activatedAt: null, createdAt: '2026-09-25T10:00:00', ...extra });
  let added: Record<string, unknown> | null = null;
  await mockApi(page, '/tenants/acme/domains', (route) => {
    if (route.request().method() === 'POST') {
      added = route.request().postDataJSON();
      stages = [[domain('PENDING_VERIFICATION', { lastError: 'Waiting for TXT _platform-verify.www.acme-builders.test' })],
        [domain('CERT_ISSUING')], [domain('ACTIVE', { certNotAfter: '2026-12-24T10:00:00', certIssuer: 'CN=Pebble Intermediate' })]];
      return json(route, stages[0][0], 201);
    }
    return json(route, stages.length > 1 ? stages.shift() : stages[0]);
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  const card = page.getByTestId('domains-card');
  await card.getByLabel('Domain').fill('www.acme-builders.test');
  await card.getByRole('button', { name: 'Add domain' }).click();
  expect(added).toEqual({ host: 'www.acme-builders.test', surface: 'WEB' });
  const row = card.getByTestId('domain-www.acme-builders.test');
  await expect(row.getByTestId('record-TXT')).toContainText('a1b2c3');
  await expect(row.getByText('Live')).toBeVisible({ timeout: 15000 });
  await expect(row).toContainText('certificate until');
  await expect(row.getByTestId('record-TXT')).toHaveCount(0);
});

test('an operator destroys an archived tenant\'s encryption keys after typing its key', async ({ page }) => {
  await signInAs(page, operator);
  const archived = { ...tenant, tenantKey: 'oldco', name: 'Old Co', subdomain: 'oldco', status: 'ARCHIVED' };
  let keysDestroyedAt: string | null = null;
  await mockApi(page, '/tenants', (route) => json(route, [{ ...archived, keysDestroyedAt }]));
  await mockApi(page, '/tenants/drafts', []);
  await mockApi(page, '/tenants/oldco', (route) => json(route, { ...archived, keysDestroyedAt }));
  await mockApi(page, '/tenants/clusters', clusters);
  await mockApi(page, '/tenants/oldco/moves', []);
  await mockApi(page, '/tenants/oldco/domains', []);
  await mockApi(page, '/tenants/oldco/placement', { tenantKey: 'oldco', clusterId: 'cluster-a', cell: 'cell-1',
    tier: 'STANDARD', epoch: 0, status: 'ARCHIVED', currentMove: null });
  let confirm: unknown = null;
  await mockApi(page, '/tenants/oldco/crypto-shred', (route) => {
    confirm = route.request().postDataJSON();
    keysDestroyedAt = '2026-09-25T10:00:00';
    return json(route, { tenantKey: 'oldco', keysDestroyed: 2, integrationsDisabled: 1, destroyedAt: keysDestroyedAt });
  });

  await page.goto('/admin/tenants');
  await page.getByRole('button', { name: 'Configure' }).click();
  const card = page.getByTestId('crypto-shred-card');
  await card.getByRole('button', { name: 'Destroy encryption keys' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: 'Destroy keys' })).toBeDisabled();
  await dialog.getByLabel('Tenant key').fill('oldco');
  await dialog.getByRole('button', { name: 'Destroy keys' }).click();
  expect(confirm).toEqual({ confirm: 'oldco' });
  await expect(card.getByTestId('keys-destroyed')).toContainText('can no longer be read');
});

test('the operator sees every workspace in the analytics warehouse, captured live from each cluster', async ({ page }) => {
  await signInAs(page, operator);
  const k = (tenantKey: string, bookings: number) => ({ tenantKey, bookings, bookingsLast30Days: bookings, cancelledBookings: 1,
    distinctCustomers: 3, paymentsCompleted: 250000, purchaseOrders: 2, purchaseOrderValue: 403840 });
  await mockApi(page, '/analytics/platform', { totals: k('*', 12), tenants: [k('acme', 9), k('bigco', 3)] });
  await mockApi(page, '/analytics/capture', [
    { clusterId: 'cluster-a', host: 'mysql', connected: true, binlogFile: 'binlog.000035', binlogPosition: 1, events: 120, lastEventAt: null },
    { clusterId: 'cluster-b', host: 'mysql-b', connected: true, binlogFile: 'binlog.000002', binlogPosition: 1, events: 8, lastEventAt: null },
  ]);
  await page.goto('/admin/analytics');
  const card = page.getByTestId('warehouse-kpis');
  await expect(card.getByTestId('kpi-bookings')).toHaveText('12');
  await expect(card.getByTestId('capture-cluster-b')).toContainText('capturing · 8 changes');
  await expect(card.getByTestId('warehouse-tenants')).toContainText('acme');
  await expect(card.getByTestId('warehouse-tenants')).toContainText('bigco');
});
