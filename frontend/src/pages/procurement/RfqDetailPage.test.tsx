import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../../services/procurementApi', async (orig) => ({
  ...(await orig<typeof import('../../services/procurementApi')>()),
  fetchRfq: vi.fn(),
  fetchMyOrganizations: vi.fn(),
  submitQuotation: vi.fn(),
  acceptQuotation: vi.fn(),
  cancelRfq: vi.fn(),
}));

import * as api from '../../services/procurementApi';
import RfqDetailPage from './RfqDetailPage';

const mocked = vi.mocked(api);

const org = (id: number, name: string, capabilities: api.Capability[]): api.Organization => ({
  id, name, gstin: null, capabilities, approvalThreshold: null, effectiveApprovalThreshold: 100000, myRole: 'OWNER',
});
const quotation = (id: number, supplier: api.OrgRef, cement: number, total: number): api.Quotation => ({
  id, supplier, status: 'SUBMITTED', validUntil: '2026-10-01', notes: null, subtotal: total, taxTotal: 0, total,
  lines: [{ rfqLineId: 11, unitPrice: cement, taxPercent: 28, amount: cement * 500 },
    { rfqLineId: 12, unitPrice: 65000, taxPercent: 18, amount: 130000 }],
  submittedAt: '2026-09-24T10:00:00',
});
const rfq = (overrides: Partial<api.RfqDetail> = {}): api.RfqDetail => ({
  id: 5, number: 'RFQ-00005', title: 'Cement and steel', buyer: { id: 1, name: 'BuildCo' }, status: 'OPEN',
  neededBy: null, deliverySite: 'Plot 12', reference: 'Booking #42', createdAt: '2026-09-24T10:00:00',
  lines: [{ id: 11, lineNo: 1, description: 'OPC 53 cement', quantity: 500, uom: 'bag' },
    { id: 12, lineNo: 2, description: 'TMT bar', quantity: 2, uom: 'tonne' }],
  invitedSuppliers: [{ id: 2, name: 'CementCo' }, { id: 3, name: 'SteelCo' }, { id: 4, name: 'SandCo' }],
  quotations: [], roles: ['BUYER'], purchaseOrderId: null,
  ...overrides,
});

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/procurement/rfqs/5']}>
        <Routes>
          <Route path="/procurement/rfqs/:rfqId" element={<RfqDetailPage />} />
          <Route path="/procurement/orders/:poId" element={<div>order page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe('RfqDetailPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows the buyer every quotation side by side, flags the lowest, and accepting opens the order', async () => {
    mocked.fetchMyOrganizations.mockResolvedValue([org(1, 'BuildCo', ['BUYER', 'CONTRACTOR'])]);
    mocked.fetchRfq.mockResolvedValue(rfq({
      quotations: [quotation(31, { id: 3, name: 'SteelCo' }, 395, 400000), quotation(32, { id: 2, name: 'CementCo' }, 400, 409400)],
    }));
    mocked.acceptQuotation.mockResolvedValue({ id: 77 } as api.PoDetail);
    renderPage();

    const table = await screen.findByTestId('quotation-comparison');
    const header = within(table).getAllByRole('columnheader');
    expect(header[1]).toHaveTextContent('SteelCo');
    expect(header[1]).toHaveTextContent('Lowest');
    expect(header[2]).not.toHaveTextContent('Lowest');
    expect(within(table).getByTestId('quote-total-32')).toHaveTextContent('₹4,09,400.00');
    expect(screen.getByText(/Waiting for: SandCo/)).toBeInTheDocument();
    expect(screen.queryByTestId('quote-form')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Accept SteelCo' }));
    expect(mocked.acceptQuotation).toHaveBeenCalledWith(5, 31);
    expect(await screen.findByText('order page')).toBeInTheDocument();
  });

  it('lets an invited supplier price every line, with a live total, and sends exactly that', async () => {
    mocked.fetchMyOrganizations.mockResolvedValue([org(2, 'CementCo', ['SUPPLIER'])]);
    mocked.fetchRfq.mockResolvedValue(rfq({ roles: ['SUPPLIER'] }));
    mocked.submitQuotation.mockResolvedValue(rfq({ roles: ['SUPPLIER'] }));
    renderPage();

    const form = await screen.findByTestId('quote-form');
    expect(screen.queryByTestId('quotation-comparison')).not.toBeInTheDocument();
    const submit = within(form).getByRole('button', { name: 'Submit quotation' });
    expect(submit).toBeDisabled();

    await userEvent.type(within(form).getByLabelText('Unit price for OPC 53 cement'), '400');
    const cementTax = within(form).getByLabelText('GST for OPC 53 cement');
    await userEvent.clear(cementTax);
    await userEvent.type(cementTax, '28');
    await userEvent.type(within(form).getByLabelText('Unit price for TMT bar'), '65000');
    // 200,000 + 28% and 130,000 + 18%
    expect(screen.getByTestId('quote-live-total')).toHaveTextContent('₹4,09,400.00');

    await userEvent.click(submit);
    expect(mocked.submitQuotation).toHaveBeenCalledWith(5, {
      supplierOrgId: 2, validUntil: null, notes: undefined,
      lines: [{ rfqLineId: 11, unitPrice: 400, taxPercent: 28 }, { rfqLineId: 12, unitPrice: 65000, taxPercent: 18 }],
    });
  });

  it('points an awarded RFQ at its order', async () => {
    mocked.fetchMyOrganizations.mockResolvedValue([org(1, 'BuildCo', ['BUYER'])]);
    mocked.fetchRfq.mockResolvedValue(rfq({ status: 'AWARDED', purchaseOrderId: 77 }));
    renderPage();
    expect(await screen.findByText(/A purchase order has been raised/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel RFQ' })).not.toBeInTheDocument();
  });
});
