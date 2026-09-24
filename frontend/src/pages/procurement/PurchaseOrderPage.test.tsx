import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../../services/procurementApi', async (orig) => ({
  ...(await orig<typeof import('../../services/procurementApi')>()),
  fetchPurchaseOrder: vi.fn(),
  approvePurchaseOrder: vi.fn(),
  rejectPurchaseOrder: vi.fn(),
  acknowledgePurchaseOrder: vi.fn(),
  recordReceipt: vi.fn(),
  submitInvoice: vi.fn(),
  decideInvoice: vi.fn(),
}));

import * as api from '../../services/procurementApi';
import PurchaseOrderPage from './PurchaseOrderPage';

const mocked = vi.mocked(api);

const po = (overrides: Partial<api.PoDetail> = {}): api.PoDetail => ({
  id: 9, number: 'PO-00009', rfqId: 5, buyer: { id: 1, name: 'BuildCo' }, supplier: { id: 2, name: 'CementCo' },
  status: 'ISSUED', subtotal: 200000, taxTotal: 56000, total: 256000, approvalThreshold: 100000,
  deliverySite: 'Plot 12', reference: 'Booking #42', roles: ['BUYER'], canApprove: false, cancelReason: null,
  approvedAt: null, acknowledgedAt: null, createdAt: '2026-09-24T10:00:00',
  lines: [{ id: 21, lineNo: 1, description: 'OPC 53 cement', quantity: 500, uom: 'bag', unitPrice: 400, taxPercent: 28,
    amount: 200000, receivedQty: 0, acceptedQty: 0, invoicedQty: 0 }],
  receipts: [], invoices: [],
  ...overrides,
});

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/procurement/orders/9']}>
        <Routes><Route path="/procurement/orders/:poId" element={<PurchaseOrderPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe('PurchaseOrderPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('offers approval only to a checker, and explains the wait to the raiser', async () => {
    mocked.fetchPurchaseOrder.mockResolvedValue(po({ status: 'PENDING_APPROVAL', canApprove: false }));
    const { unmount } = renderPage();
    expect(await screen.findByText(/An approver other than whoever raised it must approve it/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    unmount();

    mocked.fetchPurchaseOrder.mockResolvedValue(po({ status: 'PENDING_APPROVAL', canApprove: true }));
    mocked.approvePurchaseOrder.mockResolvedValue(po({ status: 'ISSUED' }));
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    expect(mocked.approvePurchaseOrder).toHaveBeenCalledWith(9);
    expect(await screen.findByTestId('po-status')).toHaveTextContent('Issued');
  });

  it('asks the supplier to acknowledge, and never offers them a goods receipt', async () => {
    mocked.fetchPurchaseOrder.mockResolvedValue(po({ roles: ['SUPPLIER'] }));
    mocked.acknowledgePurchaseOrder.mockResolvedValue(po({ roles: ['SUPPLIER'], status: 'ACKNOWLEDGED' }));
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Acknowledge' }));
    expect(await screen.findByTestId('po-status')).toHaveTextContent('Acknowledged');
    expect(screen.queryByRole('button', { name: 'Record goods receipt' })).not.toBeInTheDocument();
  });

  it('records a receipt with rejected quantity for the buyer', async () => {
    mocked.fetchPurchaseOrder.mockResolvedValue(po({ status: 'ACKNOWLEDGED' }));
    mocked.recordReceipt.mockResolvedValue(po({ status: 'PARTIALLY_RECEIVED' }));
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Record goods receipt' }));
    const received = screen.getByLabelText('Received OPC 53 cement');
    expect(received).toHaveValue(500);
    await userEvent.clear(received);
    await userEvent.type(received, '320');
    const rejected = screen.getByLabelText('Rejected OPC 53 cement');
    await userEvent.clear(rejected);
    await userEvent.type(rejected, '20');
    await userEvent.click(screen.getByRole('button', { name: 'Record receipt' }));
    expect(mocked.recordReceipt).toHaveBeenCalledWith(9, [{ poLineId: 21, receivedQty: 320, rejectedQty: 20 }], undefined);
  });

  it('shows why an invoice failed the three-way match and allows only rejecting it', async () => {
    mocked.fetchPurchaseOrder.mockResolvedValue(po({
      status: 'PARTIALLY_RECEIVED',
      invoices: [{ id: 40, invoiceNumber: 'INV-7', status: 'EXCEPTION', subtotal: 0, taxTotal: 0, total: 204800,
        matchIssues: ['Line 1: bills 400 but only 300 received, accepted and not yet invoiced'],
        lines: [], decisionNote: null, submittedAt: '2026-09-24T10:00:00' }],
    }));
    renderPage();
    const inv = await screen.findByTestId('invoice-40');
    expect(within(inv).getByText('Match failed')).toBeInTheDocument();
    expect(within(inv).getByText(/bills 400 but only 300/)).toBeInTheDocument();
    expect(within(inv).queryByRole('button', { name: 'Approve for payment' })).not.toBeInTheDocument();
    expect(within(inv).getByRole('button', { name: 'Reject' })).toBeInTheDocument();
  });
});
