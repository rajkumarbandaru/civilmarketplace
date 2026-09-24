import { test, expect, mockApi, json, signInAs } from './fixtures';

/** B2B procurement: organization → RFQ → quotations compared → PO → approval → GRN → invoice match. */

const contractor = { id: 10, name: 'Ravi Contractor', email: 'ravi@buildco.in', role: 'WORKER' };
const supplierUser = { id: 20, name: 'Meena Cement', email: 'meena@cementco.in', role: 'CUSTOMER' };

const org = (id: number, name: string, capabilities: string[], myRole = 'OWNER') => ({
  id, name, gstin: null, capabilities, approvalThreshold: null, effectiveApprovalThreshold: 100000, myRole,
});
const buildCo = org(1, 'BuildCo', ['BUYER', 'CONTRACTOR']);
const cementCo = org(2, 'CementCo', ['SUPPLIER']);

const lines = [
  { id: 11, lineNo: 1, description: 'OPC 53 cement', quantity: 500, uom: 'bag' },
  { id: 12, lineNo: 2, description: 'TMT bar 12mm', quantity: 2, uom: 'tonne' },
];
const rfq = (overrides: object = {}) => ({
  id: 5, number: 'RFQ-00005', title: 'Cement and steel for the Sharma extension', buyer: { id: 1, name: 'BuildCo' },
  status: 'OPEN', neededBy: '2026-10-10', deliverySite: 'Plot 12, Hyderabad', reference: 'Booking #42',
  createdAt: '2026-09-24T10:00:00', lines,
  invitedSuppliers: [{ id: 2, name: 'CementCo' }, { id: 3, name: 'SteelCo' }],
  quotations: [], roles: ['BUYER'], purchaseOrderId: null, ...overrides,
});
const quote = (id: number, supplier: { id: number; name: string }, cement: number, total: number) => ({
  id, supplier, status: 'SUBMITTED', validUntil: '2026-10-01', notes: null, subtotal: total, taxTotal: 0, total,
  lines: [{ rfqLineId: 11, unitPrice: cement, taxPercent: 28, amount: cement * 500 },
    { rfqLineId: 12, unitPrice: 65000, taxPercent: 18, amount: 130000 }],
  submittedAt: '2026-09-24T11:00:00',
});
const po = (overrides: object = {}) => ({
  id: 9, number: 'PO-00009', rfqId: 5, buyer: { id: 1, name: 'BuildCo' }, supplier: { id: 2, name: 'CementCo' },
  status: 'ACKNOWLEDGED', subtotal: 330000, taxTotal: 79400, total: 409400, approvalThreshold: 100000,
  deliverySite: 'Plot 12, Hyderabad', reference: 'Booking #42', roles: ['BUYER'], canApprove: false,
  cancelReason: null, approvedAt: null, acknowledgedAt: null, createdAt: '2026-09-24T12:00:00',
  lines: [
    { id: 21, lineNo: 1, description: 'OPC 53 cement', quantity: 500, uom: 'bag', unitPrice: 400, taxPercent: 28,
      amount: 200000, receivedQty: 0, acceptedQty: 0, invoicedQty: 0 },
    { id: 22, lineNo: 2, description: 'TMT bar 12mm', quantity: 2, uom: 'tonne', unitPrice: 65000, taxPercent: 18,
      amount: 130000, receivedQty: 0, acceptedQty: 0, invoicedQty: 0 },
  ],
  receipts: [], invoices: [], ...overrides,
});

test('someone without an organization is walked through registering one', async ({ page }) => {
  await signInAs(page, contractor);
  let orgs: object[] = [];
  let created: Record<string, unknown> | null = null;
  await mockApi(page, '/procurement/organizations/mine', (route) => json(route, orgs));
  await mockApi(page, '/procurement/organizations', (route) => {
    created = route.request().postDataJSON();
    orgs = [buildCo];
    return json(route, buildCo, 201);
  });
  await mockApi(page, '/procurement/rfqs', []);

  await page.goto('/procurement');
  const onboarding = page.getByTestId('procurement-onboarding');
  await expect(onboarding).toContainText('Set up your organization');
  await onboarding.getByLabel('Organization name').fill('BuildCo');
  await onboarding.getByLabel('Contractor').check();
  await onboarding.getByLabel('GSTIN').fill('36abcde1234f1z5');
  await onboarding.getByRole('button', { name: 'Create organization' }).click();

  expect(created).toEqual({ name: 'BuildCo', gstin: '36ABCDE1234F1Z5', capabilities: ['BUYER', 'CONTRACTOR'], approvalThreshold: null });
  await expect(page.getByRole('tab', { name: 'RFQs' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'New RFQ' })).toBeVisible();
});

test('a contractor raises an RFQ to suppliers, preferred ones listed first', async ({ page }) => {
  await signInAs(page, contractor);
  let sent: Record<string, unknown> | null = null;
  await mockApi(page, '/procurement/organizations/mine', [buildCo]);
  await mockApi(page, '/procurement/rfqs', (route) => {
    if (route.request().method() === 'POST') {
      sent = route.request().postDataJSON();
      return json(route, rfq(), 201);
    }
    return json(route, []);
  });
  await mockApi(page, /\/api\/v1\/procurement\/organizations\/directory/, [
    { id: 3, name: 'SteelCo', capabilities: ['SUPPLIER'], preferred: true },
    { id: 2, name: 'CementCo', capabilities: ['SUPPLIER'], preferred: false },
  ]);
  await mockApi(page, '/procurement/rfqs/5', rfq());

  await page.goto('/procurement');
  await page.getByRole('button', { name: 'New RFQ' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Title').fill('Cement and steel for the Sharma extension');
  await dialog.getByLabel('Deliver to').fill('Plot 12, Hyderabad');
  await dialog.getByLabel('Reference').fill('Booking #42');
  const first = dialog.getByTestId('rfq-line-0');
  await first.getByLabel('Item', { exact: true }).fill('OPC 53 cement');
  await first.getByLabel('Quantity').fill('500');
  await first.getByLabel('Unit', { exact: true }).fill('bag');
  await dialog.getByRole('button', { name: 'Add item' }).click();
  const second = dialog.getByTestId('rfq-line-1');
  await second.getByLabel('Item', { exact: true }).fill('TMT bar 12mm');
  await second.getByLabel('Quantity').fill('2');
  await second.getByLabel('Unit', { exact: true }).fill('tonne');

  const suppliers = dialog.getByRole('checkbox');
  await expect(suppliers.first()).toHaveAccessibleName(/SteelCo/);
  await expect(dialog.getByText('Preferred')).toBeVisible();
  await expect(dialog.getByRole('button', { name: /Send to/ })).toBeDisabled();
  await dialog.getByLabel('SteelCo').check();
  await dialog.getByLabel('CementCo').check();
  await dialog.getByRole('button', { name: 'Send to 2 suppliers' }).click();

  expect(sent).toMatchObject({
    buyerOrgId: 1, title: 'Cement and steel for the Sharma extension', reference: 'Booking #42',
    lines: [{ description: 'OPC 53 cement', quantity: 500, uom: 'bag' }, { description: 'TMT bar 12mm', quantity: 2, uom: 'tonne' }],
    supplierOrgIds: [3, 2],
  });
  await expect(page).toHaveURL(/\/procurement\/rfqs\/5$/);
  await expect(page.getByTestId('rfq-status')).toHaveText('open');
});

test('the buyer compares quotations side by side and accepting raises an order awaiting approval', async ({ page }) => {
  await signInAs(page, contractor);
  await mockApi(page, '/procurement/organizations/mine', [buildCo]);
  await mockApi(page, '/procurement/rfqs/5', rfq({
    quotations: [quote(31, { id: 3, name: 'SteelCo' }, 395, 400000), quote(32, { id: 2, name: 'CementCo' }, 400, 409400)],
  }));
  let accepted = '';
  await mockApi(page, '/procurement/rfqs/5/quotations/*/accept', (route) => {
    accepted = route.request().url();
    return json(route, po({ status: 'PENDING_APPROVAL', supplier: { id: 3, name: 'SteelCo' } }), 201);
  });
  await mockApi(page, '/procurement/purchase-orders/9', po({ status: 'PENDING_APPROVAL', supplier: { id: 3, name: 'SteelCo' } }));

  await page.goto('/procurement/rfqs/5');
  const table = page.getByTestId('quotation-comparison');
  await expect(table.getByRole('columnheader').nth(1)).toContainText('SteelCo');
  await expect(table.getByRole('columnheader').nth(1)).toContainText('Lowest');
  await expect(table.getByTestId('quote-total-32')).toHaveText('₹4,09,400.00');
  await page.getByRole('button', { name: 'Accept SteelCo' }).click();

  expect(accepted).toContain('/quotations/31/accept');
  await expect(page).toHaveURL(/\/procurement\/orders\/9$/);
  await expect(page.getByTestId('po-status')).toHaveText('Awaiting approval');
  await expect(page.getByText(/An approver other than whoever raised it must approve it/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0);
});

test('an invited supplier prices every line and sees only its own quotation', async ({ page }) => {
  await signInAs(page, supplierUser);
  let submitted: Record<string, unknown> | null = null;
  await mockApi(page, '/procurement/organizations/mine', [cementCo]);
  await mockApi(page, '/procurement/rfqs/5', rfq({ roles: ['SUPPLIER'] }));
  await mockApi(page, '/procurement/rfqs/5/quotations', (route) => {
    submitted = route.request().postDataJSON();
    return json(route, rfq({ roles: ['SUPPLIER'], quotations: [quote(32, { id: 2, name: 'CementCo' }, 400, 409400)] }));
  });

  await page.goto('/procurement/rfqs/5');
  await expect(page.getByTestId('quotation-comparison')).toHaveCount(0);
  const form = page.getByTestId('quote-form');
  await form.getByLabel('Unit price for OPC 53 cement').fill('400');
  await form.getByLabel('GST for OPC 53 cement').fill('28');
  await form.getByLabel('Unit price for TMT bar 12mm').fill('65000');
  await expect(form.getByTestId('quote-live-total')).toContainText('₹4,09,400.00');
  await form.getByRole('button', { name: 'Submit quotation' }).click();

  expect(submitted).toEqual({ supplierOrgId: 2, validUntil: null, lines: [
    { rfqLineId: 11, unitPrice: 400, taxPercent: 28 }, { rfqLineId: 12, unitPrice: 65000, taxPercent: 18 }] });
  await expect(form.getByText('Quotation sent.')).toBeVisible();
  await expect(form.getByRole('button', { name: 'Revise quotation' })).toBeVisible();
});

test('goods are received with a rejection, and a supplier invoice that bills too much fails the match', async ({ page }) => {
  await signInAs(page, contractor);
  let current = po();
  let receipt: Record<string, unknown> | null = null;
  await mockApi(page, '/procurement/purchase-orders/9', (route) => json(route, current));
  await mockApi(page, '/procurement/purchase-orders/9/receipts', (route) => {
    receipt = route.request().postDataJSON();
    current = po({
      status: 'PARTIALLY_RECEIVED',
      lines: [{ ...po().lines[0], receivedQty: 320, acceptedQty: 300 }, po().lines[1]],
      receipts: [{ id: 50, number: 'GRN-00050', notes: 'first truck', receivedAt: '2026-09-25T09:00:00',
        lines: [{ poLineId: 21, receivedQty: 320, rejectedQty: 20 }] }],
      invoices: [{ id: 60, invoiceNumber: 'CC/26/118', status: 'EXCEPTION', subtotal: 160000, taxTotal: 44800, total: 204800,
        matchIssues: ['Line 1: bills 400 but only 300 received, accepted and not yet invoiced'], lines: [],
        decisionNote: null, submittedAt: '2026-09-25T10:00:00' }],
    });
    return json(route, current);
  });

  await page.goto('/procurement/orders/9');
  await expect(page.getByTestId('po-total')).toHaveText('₹4,09,400.00');
  await page.getByRole('button', { name: 'Record goods receipt' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Received OPC 53 cement').fill('320');
  await dialog.getByLabel('Rejected OPC 53 cement').fill('20');
  await dialog.getByLabel('Received TMT bar 12mm').fill('0');
  await dialog.getByLabel('Notes (vehicle, challan, damage)').fill('first truck');
  await dialog.getByRole('button', { name: 'Record receipt' }).click();

  expect(receipt).toEqual({ notes: 'first truck', lines: [
    { poLineId: 21, receivedQty: 320, rejectedQty: 20 }, { poLineId: 22, receivedQty: 0, rejectedQty: 0 }] });
  await expect(page.getByTestId('po-status')).toHaveText('Partly received');
  await expect(page.getByTestId('po-lines')).toContainText('20 rejected');
  const invoice = page.getByTestId('invoice-60');
  await expect(invoice.getByText('Match failed')).toBeVisible();
  await expect(invoice.getByText(/bills 400 but only 300/)).toBeVisible();
  await expect(invoice.getByRole('button', { name: 'Approve for payment' })).toHaveCount(0);
});
