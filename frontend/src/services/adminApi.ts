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
