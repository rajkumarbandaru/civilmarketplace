import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../services/procurementApi', async (orig) => ({
  ...(await orig<typeof import('../../services/procurementApi')>()),
  fetchPriceLists: vi.fn(),
  saveCatalogue: vi.fn(),
  proposeContract: vi.fn(),
  decideContract: vi.fn(),
  fetchDirectory: vi.fn(),
}));

import * as api from '../../services/procurementApi';
import PriceListsPanel from './PriceListsPanel';

const mocked = vi.mocked(api);
const org = (id: number, name: string, capabilities: api.Capability[], myRole: api.MemberRole = 'OWNER'): api.Organization => ({
  id, name, gstin: null, capabilities, approvalThreshold: null, effectiveApprovalThreshold: 100000, myRole,
});
const list = (overrides: Partial<api.PriceList>): api.PriceList => ({
  id: 1, supplier: { id: 2, name: 'CementCo' }, buyer: null, name: 'CementCo catalogue', status: 'ACTIVE', contract: false,
  paymentTermsDays: 0, creditLimit: null, exposure: null, validFrom: null, validUntil: null,
  items: [{ id: 5, description: 'OPC 53 cement', uom: 'bag', unitPrice: 395, taxPercent: 28 }], roles: ['SUPPLIER'],
  createdAt: '2026-09-24T10:00:00', decidedAt: null, ...overrides,
});
const renderPanel = (orgs: api.Organization[]) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <PriceListsPanel organizations={orgs} />
  </QueryClientProvider>,
);

describe('PriceListsPanel', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lets a buyer accept a proposed contract, showing its terms', async () => {
    mocked.fetchPriceLists.mockResolvedValue([list({
      id: 9, buyer: { id: 1, name: 'BuildCo' }, name: 'Cement 2026-27', status: 'PROPOSED', contract: true,
      paymentTermsDays: 30, creditLimit: 500000, roles: ['BUYER'],
    })]);
    mocked.decideContract.mockResolvedValue(list({ id: 9, contract: true, status: 'ACTIVE' }));
    renderPanel([org(1, 'BuildCo', ['BUYER'])]);
    const card = await screen.findByTestId('contract-9');
    expect(card).toHaveTextContent('CementCo → BuildCo · Net 30 · credit limit ₹5,00,000.00');
    expect(screen.queryByTestId('catalogue-1')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Offer a contract' })).not.toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Accept' }));
    expect(mocked.decideContract).toHaveBeenCalledWith(9, 'accept');
  });

  it('lets a supplier edit its catalogue and offer a contract prefilled from it', async () => {
    mocked.fetchPriceLists.mockResolvedValue([list({})]);
    mocked.saveCatalogue.mockResolvedValue(list({}));
    mocked.fetchDirectory.mockResolvedValue([{ id: 1, name: 'BuildCo', capabilities: ['BUYER'], preferred: false, contracted: false }]);
    mocked.proposeContract.mockResolvedValue(list({ contract: true, status: 'PROPOSED' }));
    renderPanel([org(2, 'CementCo', ['SUPPLIER'])]);

    const catalogue = await screen.findByTestId('catalogue-2');
    await userEvent.click(within(catalogue).getByRole('button', { name: 'Add item' }));
    const rows = within(catalogue).getAllByLabelText('Item');
    await userEvent.type(rows[1], 'River sand');
    await userEvent.type(within(catalogue).getAllByLabelText('Unit')[1], 'cft');
    await userEvent.type(within(catalogue).getAllByLabelText('Price (₹)')[1], '58');
    await userEvent.click(within(catalogue).getByRole('button', { name: 'Save catalogue' }));
    expect(mocked.saveCatalogue).toHaveBeenCalledWith(2, [
      { description: 'OPC 53 cement', uom: 'bag', unitPrice: 395, taxPercent: 28 },
      { description: 'River sand', uom: 'cft', unitPrice: 58, taxPercent: 18 },
    ]);

    await userEvent.click(screen.getByRole('button', { name: 'Offer a contract' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Buyer'));
    await userEvent.click(await screen.findByRole('option', { name: 'BuildCo' }));
    await userEvent.type(within(dialog).getByLabelText('Contract name'), 'Cement 2026-27');
    const price = within(within(dialog).getByTestId('contract-items')).getByLabelText('Price (₹)');
    expect(price).toHaveValue(395);
    await userEvent.clear(price);
    await userEvent.type(price, '380');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Offer contract' }));
    expect(mocked.proposeContract).toHaveBeenCalledWith({
      supplierOrgId: 2, buyerOrgId: 1, name: 'Cement 2026-27', paymentTermsDays: 30, creditLimit: null,
      validFrom: null, validUntil: null, items: [{ description: 'OPC 53 cement', uom: 'bag', unitPrice: 380, taxPercent: 28 }],
    });
  });
});
