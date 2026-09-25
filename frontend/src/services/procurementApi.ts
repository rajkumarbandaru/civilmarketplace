import api from './api';

/**
 * Client for B2B procurement (`backend/procurement-service`).
 *
 * The chain is RFQ → quotation → purchase order → goods receipt (GRN) → supplier invoice, between
 * organizations of one tenant. What the signed-in person may do on a document follows from the
 * organizations they belong to, which each response reports as `roles` (BUYER, SUPPLIER).
 * Amounts arrive as numbers in rupees; quantities as numbers in the line's unit.
 */

export type Capability = 'BUYER' | 'SUPPLIER' | 'CONTRACTOR' | 'EQUIPMENT_PROVIDER';
export type MemberRole = 'OWNER' | 'APPROVER' | 'MEMBER';
export type RelationshipType = 'PREFERRED_SUPPLIER' | 'BLOCKED';
export type RfqStatus = 'OPEN' | 'AWARDED' | 'CANCELLED';
export type QuotationStatus = 'SUBMITTED' | 'ACCEPTED' | 'REJECTED';
export type PoStatus =
  | 'PENDING_APPROVAL' | 'ISSUED' | 'ACKNOWLEDGED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CLOSED' | 'CANCELLED';
export type InvoiceStatus = 'MATCHED' | 'EXCEPTION' | 'APPROVED' | 'REJECTED' | 'PAID';
export type PriceListStatus = 'ACTIVE' | 'PROPOSED' | 'DECLINED' | 'TERMINATED';
export type TradeRole = 'BUYER' | 'SUPPLIER';

export const CAPABILITY_LABELS: Record<Capability, string> = {
  BUYER: 'Buyer',
  SUPPLIER: 'Supplier',
  CONTRACTOR: 'Contractor',
  EQUIPMENT_PROVIDER: 'Equipment provider',
};

export const PO_STATUS_LABELS: Record<PoStatus, string> = {
  PENDING_APPROVAL: 'Awaiting approval',
  ISSUED: 'Issued',
  ACKNOWLEDGED: 'Acknowledged',
  PARTIALLY_RECEIVED: 'Partly received',
  RECEIVED: 'Received',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
export const formatMoney = (value: number | null | undefined) => (value == null ? '—' : inr.format(value));
export const formatQty = (value: number) => Number(value).toLocaleString('en-IN', { maximumFractionDigits: 3 });

export interface Organization {
  id: number;
  name: string;
  gstin: string | null;
  capabilities: Capability[];
  approvalThreshold: number | null;
  effectiveApprovalThreshold: number;
  myRole: MemberRole;
}

export interface OrgMember { id: number; email: string; role: MemberRole; joined: boolean }
export interface OrgRelationship { id: number; targetOrgId: number; targetOrgName: string; type: RelationshipType }
export interface OrganizationDetail { organization: Organization; members: OrgMember[]; relationships: OrgRelationship[] }
export interface DirectoryEntry { id: number; name: string; capabilities: Capability[]; preferred: boolean; contracted: boolean }

export interface OrganizationInput {
  name: string;
  gstin?: string;
  capabilities: Capability[];
  approvalThreshold?: number | null;
}

export interface OrgRef { id: number; name: string }
export interface RfqLine { id: number; lineNo: number; description: string; quantity: number; uom: string }
export interface QuoteLine { rfqLineId: number; unitPrice: number; taxPercent: number; amount: number }
export interface Quotation {
  id: number;
  supplier: OrgRef;
  status: QuotationStatus;
  validUntil: string | null;
  notes: string | null;
  subtotal: number;
  taxTotal: number;
  total: number;
  lines: QuoteLine[];
  submittedAt: string;
}
export interface RfqSummary {
  id: number;
  number: string;
  title: string;
  buyer: OrgRef;
  status: RfqStatus;
  neededBy: string | null;
  quotations: number;
  roles: TradeRole[];
  createdAt: string;
}
export interface RfqDetail extends Omit<RfqSummary, 'quotations'> {
  deliverySite: string | null;
  reference: string | null;
  lines: RfqLine[];
  invitedSuppliers: OrgRef[];
  quotations: Quotation[];
  purchaseOrderId: number | null;
  /** The caller's own prices for lines, as a supplier: CONTRACT is a ceiling, CATALOGUE a suggestion. */
  priceHints: PriceHint[];
}
export interface PriceHint {
  rfqLineId: number;
  supplierOrgId: number;
  unitPrice: number;
  taxPercent: number;
  source: 'CONTRACT' | 'CATALOGUE';
}
export interface RfqInput {
  buyerOrgId: number;
  title: string;
  deliverySite?: string;
  neededBy?: string | null;
  reference?: string;
  lines: { description: string; quantity: number; uom: string }[];
  supplierOrgIds: number[];
}
export interface QuotationInput {
  supplierOrgId: number;
  validUntil?: string | null;
  notes?: string;
  lines: { rfqLineId: number; unitPrice: number; taxPercent: number }[];
}

export interface PoLine {
  id: number;
  lineNo: number;
  description: string;
  quantity: number;
  uom: string;
  unitPrice: number;
  taxPercent: number;
  amount: number;
  dispatchedQty: number;
  receivedQty: number;
  acceptedQty: number;
  invoicedQty: number;
}
export interface Dispatch {
  id: number;
  number: string;
  vehicleNumber: string;
  transporter: string | null;
  ewayBillNumber: string | null;
  consignmentValue: number;
  lines: { poLineId: number; quantity: number }[];
  receiptId: number | null;
  dispatchedAt: string;
}
export interface Receipt {
  id: number;
  number: string;
  dispatchId: number | null;
  notes: string | null;
  lines: { poLineId: number; receivedQty: number; rejectedQty: number }[];
  receivedAt: string;
}
export interface SupplierInvoice {
  id: number;
  invoiceNumber: string;
  status: InvoiceStatus;
  subtotal: number;
  taxTotal: number;
  total: number;
  matchIssues: string[];
  lines: { poLineId: number; quantity: number; unitPrice: number; taxPercent: number }[];
  decisionNote: string | null;
  dueDate: string | null;
  paidAt: string | null;
  paymentReference: string | null;
  submittedAt: string;
}
export interface PoSummary {
  id: number;
  number: string;
  buyer: OrgRef;
  supplier: OrgRef;
  status: PoStatus;
  total: number;
  roles: TradeRole[];
  createdAt: string;
}
export interface PoDetail extends Omit<PoSummary, 'total'> {
  rfqId: number;
  subtotal: number;
  taxTotal: number;
  total: number;
  approvalThreshold: number;
  /** Net N from the contract the order was placed under; 0 = due on approval. */
  paymentTermsDays: number;
  contractId: number | null;
  deliverySite: string | null;
  reference: string | null;
  lines: PoLine[];
  dispatches: Dispatch[];
  receipts: Receipt[];
  invoices: SupplierInvoice[];
  canApprove: boolean;
  cancelReason: string | null;
  approvedAt: string | null;
  acknowledgedAt: string | null;
}

const BASE = '/procurement';

export const fetchMyOrganizations = async () =>
  (await api.get<Organization[]>(`${BASE}/organizations/mine`)).data;
export const createOrganization = async (input: OrganizationInput) =>
  (await api.post<Organization>(`${BASE}/organizations`, input)).data;
export const fetchOrganization = async (id: number) =>
  (await api.get<OrganizationDetail>(`${BASE}/organizations/${id}`)).data;
export const updateOrganization = async (id: number, input: OrganizationInput) =>
  (await api.put<OrganizationDetail>(`${BASE}/organizations/${id}`, input)).data;
export const addMember = async (id: number, email: string, role: MemberRole) =>
  (await api.post<OrganizationDetail>(`${BASE}/organizations/${id}/members`, { email, role })).data;
export const removeMember = async (id: number, memberId: number) =>
  (await api.delete<OrganizationDetail>(`${BASE}/organizations/${id}/members/${memberId}`)).data;
export const setRelationship = async (id: number, targetOrgId: number, type: RelationshipType) =>
  (await api.put<OrganizationDetail>(`${BASE}/organizations/${id}/relationships`, { targetOrgId, type })).data;
export const removeRelationship = async (id: number, relationshipId: number) =>
  (await api.delete<OrganizationDetail>(`${BASE}/organizations/${id}/relationships/${relationshipId}`)).data;
export const fetchDirectory = async (capability?: Capability, asOrg?: number) =>
  (await api.get<DirectoryEntry[]>(`${BASE}/organizations/directory`, { params: { capability, asOrg } })).data;

export const fetchRfqs = async () => (await api.get<RfqSummary[]>(`${BASE}/rfqs`)).data;
export const fetchRfq = async (id: number) => (await api.get<RfqDetail>(`${BASE}/rfqs/${id}`)).data;
export const createRfq = async (input: RfqInput) => (await api.post<RfqDetail>(`${BASE}/rfqs`, input)).data;
export const submitQuotation = async (rfqId: number, input: QuotationInput) =>
  (await api.post<RfqDetail>(`${BASE}/rfqs/${rfqId}/quotations`, input)).data;
export const acceptQuotation = async (rfqId: number, quotationId: number) =>
  (await api.post<PoDetail>(`${BASE}/rfqs/${rfqId}/quotations/${quotationId}/accept`)).data;
export const cancelRfq = async (rfqId: number) => (await api.post<RfqDetail>(`${BASE}/rfqs/${rfqId}/cancel`)).data;

export const fetchPurchaseOrders = async () => (await api.get<PoSummary[]>(`${BASE}/purchase-orders`)).data;
export const fetchPurchaseOrder = async (id: number) =>
  (await api.get<PoDetail>(`${BASE}/purchase-orders/${id}`)).data;
export const approvePurchaseOrder = async (id: number) =>
  (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/approve`)).data;
export const rejectPurchaseOrder = async (id: number, note?: string) =>
  (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/reject`, { note })).data;
export const acknowledgePurchaseOrder = async (id: number) =>
  (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/acknowledge`)).data;
export const recordReceipt = async (
  id: number, lines: { poLineId: number; receivedQty: number; rejectedQty: number }[], notes?: string, dispatchId?: number | null,
) => (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/receipts`, { lines, notes, dispatchId: dispatchId ?? null })).data;

/** Above this value of goods (incl. tax) GST law requires an e-way bill; the server enforces it too. */
export const EWAY_BILL_THRESHOLD = 50000;

export const dispatchGoods = async (id: number, input: {
  vehicleNumber: string; transporter?: string; ewayBillNumber?: string; lines: { poLineId: number; quantity: number }[];
}) => (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/dispatches`, input)).data;

export interface PaymentCheckout {
  invoiceId: number;
  paymentId: number;
  razorpayOrderId: string;
  razorpayKeyId: string;
  amount: number;
  description: string;
}
export const startInvoicePayment = async (poId: number, invoiceId: number) =>
  (await api.post<PaymentCheckout>(`${BASE}/purchase-orders/${poId}/invoices/${invoiceId}/pay`)).data;

// ---------------------------------------------------------------- price lists and contracts

export interface PriceListItem { id?: number; description: string; uom: string; unitPrice: number; taxPercent: number }
export interface PriceList {
  id: number;
  supplier: OrgRef;
  buyer: OrgRef | null;
  name: string;
  status: PriceListStatus;
  contract: boolean;
  paymentTermsDays: number;
  creditLimit: number | null;
  /** What the buyer has open with the supplier under an active contract. */
  exposure: number | null;
  validFrom: string | null;
  validUntil: string | null;
  items: PriceListItem[];
  roles: TradeRole[];
  createdAt: string;
  decidedAt: string | null;
}
export interface ContractInput {
  supplierOrgId: number;
  buyerOrgId: number;
  name: string;
  paymentTermsDays: number;
  creditLimit?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  items: PriceListItem[];
}
export const fetchPriceLists = async () => (await api.get<PriceList[]>(`${BASE}/price-lists`)).data;
export const saveCatalogue = async (supplierOrgId: number, items: PriceListItem[], name?: string) =>
  (await api.put<PriceList>(`${BASE}/price-lists/catalogue`, { supplierOrgId, name, items })).data;
export const proposeContract = async (input: ContractInput) =>
  (await api.post<PriceList>(`${BASE}/price-lists/contracts`, input)).data;
export const decideContract = async (id: number, action: 'accept' | 'decline' | 'terminate') =>
  (await api.post<PriceList>(`${BASE}/price-lists/contracts/${id}/${action}`)).data;

// ---------------------------------------------------------------- party migration

/** Account types that already are a business, and what their organization does. */
export const ROLE_CAPABILITIES: Record<string, Capability[]> = {
  MATERIAL_SUPPLIER: ['SUPPLIER'],
  LABOUR_CONTRACTOR: ['CONTRACTOR', 'BUYER'],
  EQUIPMENT_RENTAL: ['EQUIPMENT_PROVIDER'],
};
export interface MigrationEntry {
  userId: number; email: string; name: string; role: string; capabilities: Capability[];
  outcome: 'CREATED' | 'WOULD_CREATE' | 'ALREADY_MIGRATED'; organizationId: number | null; catalogueItems: number;
}
export interface MigrationReport { dryRun: boolean; accounts: number; created: number; alreadyMigrated: number; entries: MigrationEntry[] }
export const createOrganizationFromProfile = async () =>
  (await api.post<Organization>(`${BASE}/organizations/from-profile`)).data;
export const migrateAccounts = async (dryRun: boolean) =>
  (await api.post<MigrationReport>(`${BASE}/organizations/migration`, null, { params: { dryRun } })).data;
export const submitInvoice = async (
  id: number, invoiceNumber: string, lines: { poLineId: number; quantity: number; unitPrice: number; taxPercent: number }[],
) => (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/invoices`, { invoiceNumber, lines })).data;
export const decideInvoice = async (id: number, invoiceId: number, approve: boolean, note?: string) =>
  (await api.post<PoDetail>(`${BASE}/purchase-orders/${id}/invoices/${invoiceId}/${approve ? 'approve' : 'reject'}`, { note })).data;

/** Line amount and tax as the server computes them: rounded per line to paise. */
export const lineTotals = (lines: { quantity: number; unitPrice: number; taxPercent: number }[]) => {
  const round = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
  return lines.reduce(
    (acc, l) => {
      const amount = round(l.quantity * l.unitPrice);
      const tax = round((amount * l.taxPercent) / 100);
      return { subtotal: round(acc.subtotal + amount), tax: round(acc.tax + tax), total: round(acc.total + amount + tax) };
    },
    { subtotal: 0, tax: 0, total: 0 },
  );
};
