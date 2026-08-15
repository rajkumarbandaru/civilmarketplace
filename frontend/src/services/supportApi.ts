import api from './api';

/**
 * Client for support-service's ticketing API (`backend/support-service`).
 *
 * The endpoints identify the caller from `X-User-Id` / `X-User-Role`, which the gateway's JwtAuth
 * filter injects from the bearer token — so nothing here sends a user id, and nothing here should
 * start to. A caller-supplied id would be a claim about who you are that the gateway has already
 * settled from the token.
 */

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

/** Mirrors `SupportTicket`; the entity is returned directly, so field names track it exactly. */
export interface SupportTicket {
  id: number;
  reporterId: number;
  assigneeId: number | null;
  subject: string;
  description: string;
  category: string | null;
  priority: TicketPriority;
  status: TicketStatus;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors `TicketMessage`. */
export interface TicketMessage {
  id: number;
  ticketId: number;
  senderId: number;
  body: string;
  createdAt: string;
}

/** Spring Data's `Page` as it serialises; only the fields the UI actually reads. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateTicketRequest {
  subject: string;
  description: string;
  category?: string;
  priority?: TicketPriority;
}

const BASE = '/support/tickets';
const ADMIN_BASE = '/admin/support/tickets';

export const createTicket = async (request: CreateTicketRequest): Promise<SupportTicket> => {
  const { data } = await api.post<SupportTicket>(BASE, request);
  return data;
};

export const fetchMyTickets = async (
  status?: TicketStatus | null,
  page = 0,
  size = 20
): Promise<Page<SupportTicket>> => {
  const { data } = await api.get<Page<SupportTicket>>(BASE, {
    // Omitted rather than sent empty: the endpoint treats a blank status as a filter value.
    params: { ...(status ? { status } : {}), page, size },
  });
  return data;
};

export const fetchTicket = async (ticketId: number): Promise<SupportTicket> => {
  const { data } = await api.get<SupportTicket>(`${BASE}/${ticketId}`);
  return data;
};

export const fetchTicketMessages = async (ticketId: number): Promise<TicketMessage[]> => {
  const { data } = await api.get<TicketMessage[]>(`${BASE}/${ticketId}/messages`);
  return data;
};

export const replyToTicket = async (ticketId: number, body: string): Promise<TicketMessage> => {
  const { data } = await api.post<TicketMessage>(`${BASE}/${ticketId}/messages`, { body });
  return data;
};

export const changeTicketStatus = async (
  ticketId: number,
  status: TicketStatus,
  reason?: string
): Promise<SupportTicket> => {
  const { data } = await api.patch<SupportTicket>(`${BASE}/${ticketId}/status`, { status, reason });
  return data;
};

// ---------------------------------------------------------------------------- staff queue

export const fetchAllTickets = async (
  status?: TicketStatus | null,
  page = 0,
  size = 20
): Promise<Page<SupportTicket>> => {
  const { data } = await api.get<Page<SupportTicket>>(ADMIN_BASE, {
    params: { ...(status ? { status } : {}), page, size },
  });
  return data;
};

export const assignTicket = async (
  ticketId: number,
  assigneeId: number
): Promise<SupportTicket> => {
  const { data } = await api.patch<SupportTicket>(`${ADMIN_BASE}/${ticketId}/assign`, {
    assigneeId,
  });
  return data;
};

/** Chip colour per status, shared by the customer page and the staff queue so they cannot drift. */
export const statusColor = (
  status: TicketStatus
): 'info' | 'warning' | 'success' | 'default' => {
  switch (status) {
    case 'OPEN':
      return 'info';
    case 'IN_PROGRESS':
      return 'warning';
    case 'RESOLVED':
      return 'success';
    case 'CLOSED':
    default:
      return 'default';
  }
};

export const priorityColor = (
  priority: TicketPriority
): 'default' | 'info' | 'warning' | 'error' => {
  switch (priority) {
    case 'LOW':
      return 'default';
    case 'MEDIUM':
      return 'info';
    case 'HIGH':
      return 'warning';
    case 'URGENT':
    default:
      return 'error';
  }
};

/** `OPEN` → "Open", not `OPEN`. The enum is a wire value, not a label. */
export const humanStatus = (status: TicketStatus): string =>
  status.charAt(0) + status.slice(1).toLowerCase().replace('_', ' ');
