import api from './api';

/**
 * Client for audit-service's admin APIs (`backend/audit-service`).
 *
 * The audit log is append-only by design — there is deliberately no create, update or delete here.
 * Anything that mutates a recorded event is a compliance defect, so this module offers no way to
 * express one.
 */

/** Mirrors `AuditEvent`. */
export interface AuditEvent {
  id: number;
  sourceService: string;
  actorId: number | null;
  actorRole: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  subjectUserId: number | null;
  beforeState: string | null;
  afterState: string | null;
  reason: string | null;
  recordCount: number | null;
  occurredAt: string;
  recordedAt: string | null;
  previousHash: string | null;
  eventHash: string;
}

/** Mirrors `AccessAnomalyAlert` — bulk-access detection, not a failed login. */
export interface AccessAnomaly {
  id: number;
  actorId: number | null;
  entityType: string;
  recordsAccessed: number;
  windowMinutes: number;
  detail: string | null;
  acknowledged: boolean;
  createdAt: string;
}

export interface AuditEventPage {
  success: boolean;
  data: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditFilters {
  actorId?: string;
  subjectUserId?: string;
  entityType?: string;
  action?: string;
  from?: string;
  to?: string;
}

const BASE = '/admin/audit';

/** Blank filter fields are dropped — the backend treats an empty string as a value to match. */
const cleaned = (filters: AuditFilters): Record<string, string> =>
  Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== '')
  ) as Record<string, string>;

export const fetchAuditEvents = async (
  filters: AuditFilters,
  page = 0,
  size = 25
): Promise<AuditEventPage> => {
  const { data } = await api.get<AuditEventPage>(`${BASE}/events`, {
    params: { ...cleaned(filters), page, size },
  });
  return data;
};

export const fetchAnomalies = async (
  page = 0,
  size = 20
): Promise<{ success: boolean; data: AccessAnomaly[]; totalElements: number }> => {
  const { data } = await api.get(`${BASE}/anomalies`, { params: { page, size } });
  return data;
};

/**
 * Verifies the log's hash chain. The shape is whatever `verifyIntegrity()` returns, so it is read
 * defensively rather than typed into a contract the service has not promised.
 */
export const verifyIntegrity = async (): Promise<Record<string, unknown>> => {
  const { data } = await api.get(`${BASE}/integrity`);
  return data;
};

/** Every audit record held about one user — the right-to-access export. */
export const exportUserAudit = async (userId: number): Promise<Record<string, unknown>> => {
  const { data } = await api.get(`${BASE}/export`, { params: { userId } });
  return data;
};

/** Colours actions by how consequential they are, so a delete does not read like a login. */
export const actionColor = (
  action: string
): 'default' | 'info' | 'success' | 'warning' | 'error' => {
  const value = action.toUpperCase();
  if (value.includes('DELETE') || value.includes('ERASE')) return 'error';
  if (value.includes('UPDATE') || value.includes('CHANGE')) return 'warning';
  if (value.includes('CREATE')) return 'success';
  if (value.includes('READ') || value.includes('VIEW') || value.includes('EXPORT')) return 'info';
  return 'default';
};
