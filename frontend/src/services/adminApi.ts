import api from './api';
import { PaginationMeta } from '../types/admin';

const ADMIN_BASE = '/admin';

export interface ApiListResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  totalElements?: number;
  totalPages?: number;
  page?: number;
  size?: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp?: string;
}

// ============================================================================
// Dashboard
// ============================================================================

export interface DashboardStats {
  totalUsers: number;
  activeBookings: number;
  monthlyRevenue: number;
  pendingActions: number;
  userGrowth: string;
  bookingGrowth: string;
  revenueGrowth: string;
  pendingActionsChange: string;
  recentActivity: RecentActivityItem[];
  topCities: CityStat[];
  platformOverview: PlatformOverview;
}

export interface RecentActivityItem {
  action: string;
  user: string;
  time: string;
  type: string;
}

export interface CityStat {
  name: string;
  users: number;
  percentage: number;
}

export interface PlatformOverview {
  totalEngineers: number;
  activeProjects: number;
  pendingVerifications: number;
  disputes: number;
  cancelledBookings: number;
  averageRating: number;
}

export const dashboardApi = {
  getDashboard: () =>
    api.get<ApiResponse<DashboardStats>>(`${ADMIN_BASE}/dashboard`),

  getDashboardStats: () =>
    api.get<ApiResponse<{ totalUsers: number; activeBookings: number; monthlyRevenue: number; pendingActions: number }>>(`${ADMIN_BASE}/dashboard/stats`),

  getActivity: () =>
    api.get<ApiResponse<RecentActivityItem[]>>(`${ADMIN_BASE}/dashboard/activity`),

  getTopCities: () =>
    api.get<ApiResponse<CityStat[]>>(`${ADMIN_BASE}/dashboard/cities`),
};

// ============================================================================
// Users
// ============================================================================

export interface AdminUser {
  id: number;
  name: string;
  email: string;
  phone: string;
  role: string;
  status: string;
  city: string;
  profilePicture?: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  bookings: number;
  rating: number;
  joinedAt: string;
  lastLoginAt?: string;
}

export interface UpdateUserRequest {
  name?: string;
  email?: string;
  phone?: string;
  role?: string;
  status?: string;
}

export interface UpdateUserStatusRequest {
  status: string;
  reason?: string;
}

export const userApi = {
  getUsers: (params?: { page?: number; size?: number; search?: string; role?: string; status?: string }) =>
    api.get<ApiListResponse<AdminUser[]>>(`${ADMIN_BASE}/users`, { params }),

  getUserById: (userId: number) =>
    api.get<ApiResponse<AdminUser>>(`${ADMIN_BASE}/users/${userId}`),

  updateUser: (userId: number, data: UpdateUserRequest) =>
    api.put<ApiResponse<AdminUser>>(`${ADMIN_BASE}/users/${userId}`, data),

  updateUserStatus: (userId: number, data: UpdateUserStatusRequest) =>
    api.put<ApiResponse<AdminUser>>(`${ADMIN_BASE}/users/${userId}/status`, data),

  deleteUser: (userId: number) =>
    api.delete<ApiResponse<null>>(`${ADMIN_BASE}/users/${userId}`),

  getUserStats: () =>
    api.get<ApiResponse<{ totalUsers: number; pendingVerifications: number }>>(`${ADMIN_BASE}/users/stats`),
};

// ============================================================================
// Categories
// ============================================================================

export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  description?: string;
  icon?: string;
  image?: string;
  parentId?: number;
  parentName?: string;
  sortOrder: number;
  active: boolean;
  servicesCount: number;
}

export interface CreateCategoryRequest {
  name: string;
  slug: string;
  description?: string;
  icon?: string;
  image?: string;
  parentId?: number;
  sortOrder?: number;
}

export interface UpdateCategoryRequest {
  name?: string;
  slug?: string;
  description?: string;
  icon?: string;
  image?: string;
  parentId?: number;
  sortOrder?: number;
  active?: boolean;
}

export const categoryApi = {
  getCategories: () =>
    api.get<ApiListResponse<AdminCategory[]>>(`${ADMIN_BASE}/categories`),

  createCategory: (data: CreateCategoryRequest) =>
    api.post<ApiResponse<AdminCategory>>(`${ADMIN_BASE}/categories`, data),

  updateCategory: (categoryId: number, data: UpdateCategoryRequest) =>
    api.put<ApiResponse<AdminCategory>>(`${ADMIN_BASE}/categories/${categoryId}`, data),

  deleteCategory: (categoryId: number) =>
    api.delete<ApiResponse<null>>(`${ADMIN_BASE}/categories/${categoryId}`),

  toggleCategoryStatus: (categoryId: number) =>
    api.put<ApiResponse<AdminCategory>>(`${ADMIN_BASE}/categories/${categoryId}/status`, {}),
};

// ============================================================================
// Service catalogue (the items the public Services page lists)
// ============================================================================

export type ServiceMediaType = 'IMAGE' | 'VIDEO' | 'ANIMATION';

export interface AdminServiceOffering {
  id: number;
  slug: string;
  title: string;
  category: string;
  icon?: string | null;
  price?: string | null;
  /** Photo, video or animation shown on the card in place of the icon. */
  mediaUrl?: string | null;
  mediaType?: ServiceMediaType | null;
  rating: number;
  reviews: number;
  /** Comma-separated search aliases ("rebar, tmt, sariya"). */
  aliases?: string | null;
  sortOrder: number;
  active: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Create and update take the same body; on update an empty `slug` keeps the existing URL. */
export interface ServiceOfferingRequest {
  title: string;
  category: string;
  slug?: string;
  icon?: string;
  price?: string;
  mediaUrl?: string;
  mediaType?: ServiceMediaType | '';
  rating?: number;
  reviews?: number;
  aliases?: string;
  sortOrder?: number;
  active?: boolean;
}

export const serviceCatalogueApi = {
  getServices: () =>
    api.get<ApiListResponse<AdminServiceOffering[]>>(`${ADMIN_BASE}/services`),

  createService: (data: ServiceOfferingRequest) =>
    api.post<ApiResponse<AdminServiceOffering>>(`${ADMIN_BASE}/services`, data),

  updateService: (serviceId: number, data: ServiceOfferingRequest) =>
    api.put<ApiResponse<AdminServiceOffering>>(`${ADMIN_BASE}/services/${serviceId}`, data),

  deleteService: (serviceId: number) =>
    api.delete<ApiResponse<null>>(`${ADMIN_BASE}/services/${serviceId}`),

  toggleServiceStatus: (serviceId: number) =>
    api.put<ApiResponse<AdminServiceOffering>>(`${ADMIN_BASE}/services/${serviceId}/status`, {}),
};

// ============================================================================
// Bookings
// ============================================================================

export interface AdminBooking {
  id: number;
  bookingCode: string;
  customerName: string;
  customerId?: number;
  workerName: string;
  workerId?: number;
  serviceName: string;
  serviceCategory?: string;
  status: string;
  amount: number;
  city: string;
  scheduledDate?: string;
  createdAt: string;
  paymentStatus: string;
  paymentMethod?: string;
  description?: string;
  cancellationReason?: string;
  completedAt?: string;
}

export interface UpdateBookingStatusRequest {
  status: string;
  reason?: string;
}

export interface CompleteBookingRequest {
  finalCost: number;
}

export const bookingApi = {
  getBookings: (params?: { page?: number; size?: number; search?: string; status?: string; paymentStatus?: string }) =>
    api.get<ApiListResponse<AdminBooking[]>>(`${ADMIN_BASE}/bookings`, { params }),

  getBookingDetail: (bookingId: number) =>
    api.get<ApiResponse<AdminBooking>>(`${ADMIN_BASE}/bookings/${bookingId}`),

  updateBookingStatus: (bookingId: number, data: UpdateBookingStatusRequest) =>
    api.put<ApiResponse<AdminBooking>>(`${ADMIN_BASE}/bookings/${bookingId}/status`, data),

  completeBooking: (bookingId: number, data: CompleteBookingRequest) =>
    api.post<ApiResponse<AdminBooking>>(`${ADMIN_BASE}/bookings/${bookingId}/complete`, data),

  cancelBooking: (bookingId: number, reason?: string) =>
    api.post<ApiResponse<AdminBooking>>(`${ADMIN_BASE}/bookings/${bookingId}/cancel`, { reason }),

  getBookingStats: () =>
    api.get<ApiResponse<{ activeBookings: number; pendingCount: number; completedCount: number; disputedCount: number; totalBookings: number }>>(`${ADMIN_BASE}/bookings/stats`),
};

// ============================================================================
// Analytics
// ============================================================================

export interface AnalyticsData {
  growthMetrics: GrowthMetric[];
  monthlyTrend: MonthlyTrend[];
  topCategories: CategoryPerformance[];
  cityPerformance: CityPerformance[];
  userGrowth: { totalUsers: number; averageMonthlyGrowth: number };
}

export interface GrowthMetric {
  label: string;
  value: string;
  trend: string;
  icon?: string;
  color?: string;
}

export interface MonthlyTrend {
  month: string;
  users: number;
  bookings: number;
  revenue: number;
}

export interface CategoryPerformance {
  name: string;
  bookings: number;
  growth: string;
}

export interface CityPerformance {
  city: string;
  users: string;
  bookings: string;
  revenue: string;
  growth: string;
}

export const analyticsApi = {
  getAnalytics: () =>
    api.get<ApiResponse<AnalyticsData>>(`${ADMIN_BASE}/analytics`),

  getGrowthMetrics: () =>
    api.get<ApiResponse<GrowthMetric[]>>(`${ADMIN_BASE}/analytics/growth`),

  getMonthlyTrends: () =>
    api.get<ApiResponse<MonthlyTrend[]>>(`${ADMIN_BASE}/analytics/revenue-trend`),

  getTopCategories: () =>
    api.get<ApiResponse<CategoryPerformance[]>>(`${ADMIN_BASE}/analytics/categories`),

  getCityPerformance: () =>
    api.get<ApiResponse<CityPerformance[]>>(`${ADMIN_BASE}/analytics/cities`),
};

// ============================================================================
// Revenue
// ============================================================================

export interface RevenueData {
  summary: RevenueSummary;
  monthlyRevenue: MonthlyRevenue[];
  breakdown: RevenueBreakdown;
  recentTransactions: Transaction[];
}

export interface RevenueSummary {
  totalRevenueMtd: number;
  platformFees: number;
  pendingPayouts: number;
  refundsMtd: number;
  revenueChange: string;
  platformFeePercentage: string;
  pendingPayoutWorkers: number;
  refundChange: string;
}

export interface MonthlyRevenue {
  month: string;
  revenue: number;
  fees: number;
  payouts: number;
  profit: number;
}

export interface RevenueBreakdown {
  items: BreakdownItem[];
}

export interface BreakdownItem {
  label: string;
  value: number;
  percentage: number;
  color: string;
}

export interface Transaction {
  transactionId: string;
  bookingCode: string;
  customerName: string;
  amount: number;
  type: string;
  status: string;
  date: string;
}

// ============================================================================
// Reports
// ============================================================================

export interface ReportDefinition {
  key: string;
  label: string;
  description: string;
  category: string;
  /** Column headings, in the order the export writes them. */
  columns: string[];
}

export interface ReportCatalogue {
  reports: ReportDefinition[];
  generatedAt: string;
}

export interface ReportPreview {
  key: string;
  label: string;
  columns: string[];
  /** Rows keyed by column heading, already projected to the report's columns. */
  rows: Record<string, string>[];
  /** How many rows the full export holds — the preview is only the first page of them. */
  totalRows: number;
  generatedAt: string;
}

export const reportApi = {
  getReports: () =>
    api.get<ApiResponse<ReportCatalogue>>(`${ADMIN_BASE}/reports`),

  getReportPreview: (key: string, limit = 25) =>
    api.get<ApiResponse<ReportPreview>>(`${ADMIN_BASE}/reports/${key}`, { params: { limit } }),

  /** The full report as CSV. Returned as a blob so the caller can hand it straight to a download. */
  exportReport: (key: string) =>
    api.get<Blob>(`${ADMIN_BASE}/reports/${key}/export`, { responseType: 'blob' }),
};

// ============================================================================
// Invoices
// ============================================================================

export type InvoiceStatus = 'PAID' | 'PENDING' | 'REFUNDED' | 'CANCELLED';

export interface InvoiceLine {
  label: string;
  amount: number;
}

export interface AdminInvoice {
  invoiceNumber: string;
  paymentCode: string;
  bookingId: number;
  bookingCode: string;
  customerId: number;
  customerName: string | null;
  customerEmail: string | null;
  subtotal: number;
  platformFee: number;
  gstAmount: number;
  total: number;
  refundAmount: number;
  currency: string;
  status: InvoiceStatus;
  paymentStatus: string;
  paymentMethod: string | null;
  description: string | null;
  issuedAt: string | null;
  paidAt: string | null;
  refundedAt: string | null;
  /** Detail only — the billing lines the total is made of. */
  lines?: InvoiceLine[];
  razorpayPaymentId?: string | null;
  refundReason?: string | null;
  failureReason?: string | null;
}

export interface InvoiceSummary {
  totalBilled: number;
  totalCollected: number;
  totalOutstanding: number;
  totalRefunded: number;
  invoiceCount: number;
  paidCount: number;
  pendingCount: number;
  refundedCount: number;
  failedCount: number;
}

export const invoiceApi = {
  getInvoices: (params?: { page?: number; size?: number; status?: string; search?: string }) =>
    api.get<ApiListResponse<AdminInvoice[]>>(`${ADMIN_BASE}/invoices`, { params }),

  getInvoiceSummary: () =>
    api.get<ApiResponse<InvoiceSummary>>(`${ADMIN_BASE}/invoices/summary`),

  getInvoice: (invoiceNumber: string) =>
    api.get<ApiResponse<AdminInvoice>>(`${ADMIN_BASE}/invoices/${invoiceNumber}`),

  /**
   * Raises a new invoice against a booking. Platform fee and GST are applied server-side from the
   * configured percentages, so only the subtotal is sent — the browser never decides what is owed.
   */
  raiseInvoice: (command: RaiseInvoiceCommand) =>
    api.post<ApiResponse<AdminInvoice>>(`${ADMIN_BASE}/invoices`, command),
};

export interface RaiseInvoiceCommand {
  bookingId: number;
  customerId: number;
  /** Subtotal in rupees, before platform fee and GST. */
  amount: number;
  description?: string;
}

// ============================================================================
// Platform settings
// ============================================================================

export type SettingType = 'TEXT' | 'EMAIL' | 'NUMBER' | 'PERCENT' | 'BOOLEAN' | 'CHOICE';

export interface PlatformSetting {
  key: string;
  label: string;
  help: string;
  type: SettingType;
  /** Always a string; `type` says how to read it. */
  value: string;
  defaultValue: string;
  /** True when an admin has overridden it, as opposed to it merely equalling the default. */
  customised: boolean;
  choices: string[];
  min: number | null;
  max: number | null;
}

export interface SettingsGroup {
  group: string;
  settings: PlatformSetting[];
}

export interface PlatformSettingsData {
  groups: SettingsGroup[];
}

export const settingsApi = {
  getSettings: () =>
    api.get<ApiResponse<PlatformSettingsData>>(`${ADMIN_BASE}/settings`),

  /** Only the changed keys need to be sent; anything omitted is left as it is. */
  updateSettings: (changes: Record<string, string>) =>
    api.put<ApiResponse<PlatformSettingsData>>(`${ADMIN_BASE}/settings`, changes),

  resetSetting: (key: string) =>
    api.delete<ApiResponse<PlatformSettingsData>>(`${ADMIN_BASE}/settings/${key}`),
};

export const revenueApi = {
  getRevenueData: () =>
    api.get<ApiResponse<RevenueData>>(`${ADMIN_BASE}/revenue`),

  getRevenueSummary: () =>
    api.get<ApiResponse<RevenueSummary>>(`${ADMIN_BASE}/revenue/summary`),

  getMonthlyRevenue: () =>
    api.get<ApiResponse<MonthlyRevenue[]>>(`${ADMIN_BASE}/revenue/monthly`),

  getRevenueBreakdown: () =>
    api.get<ApiResponse<RevenueBreakdown>>(`${ADMIN_BASE}/revenue/breakdown`),

  getRecentTransactions: (params?: { page?: number; size?: number }) =>
    api.get<ApiResponse<Transaction[]>>(`${ADMIN_BASE}/revenue/transactions`, { params }),
};
