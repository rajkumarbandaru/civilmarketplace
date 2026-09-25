import api from './api';

/** The analytics warehouse (`backend/analytics-service`), fed by change-data capture from every cluster. */

export interface TenantKpis {
  tenantKey: string;
  bookings: number;
  bookingsLast30Days: number;
  cancelledBookings: number;
  distinctCustomers: number;
  paymentsCompleted: number;
  purchaseOrders: number;
  purchaseOrderValue: number;
}
export interface Breakdown { label: string; count: number }
export interface WorkspaceKpis { totals: TenantKpis; bookingsByStatus: Breakdown[]; bookingsByCity: Breakdown[] }
export interface PlatformKpis { totals: TenantKpis; tenants: TenantKpis[] }
export interface CaptureStatus {
  clusterId: string; host: string; connected: boolean; binlogFile: string | null; binlogPosition: number;
  events: number; lastEventAt: string | null;
}

export const fetchWorkspaceKpis = async () => (await api.get<WorkspaceKpis>('/analytics/workspace')).data;
export const fetchPlatformKpis = async () => (await api.get<PlatformKpis>('/analytics/platform')).data;
export const fetchCaptureStatus = async () => (await api.get<CaptureStatus[]>('/analytics/capture')).data;
