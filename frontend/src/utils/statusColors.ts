/**
 * Status chip colours, in one place per domain.
 *
 * `DashboardPage` and `BookingManagement` each carried a booking-status map, and the dashboard's
 * knew five of the thirteen statuses a booking can hold. A booking sitting in ASSIGNED, DISPUTED or
 * AWAITING_PAYMENT rendered as neutral grey on the member dashboard and as a real colour on the
 * admin screen — the same booking, two different readings, with grey implying "nothing to see".
 *
 * The full map is below and both screens use it. Support-ticket colours already lived in one place
 * (`supportApi.statusColor`, which returns MUI palette names rather than hex) and are left there.
 */

/** Neutral grey. Returned for any status not listed — a new backend status renders readably. */
export const NEUTRAL_STATUS_COLOR = '#94a3b8';

/**
 * Every status `booking-service` can put on a booking.
 *
 * Grouped by meaning, not alphabetically: amber is waiting on someone, blue is agreed, violet is
 * in flight, green is done, red is dead, orange wants attention.
 */
export const BOOKING_STATUS_COLORS: Record<string, string> = {
  PENDING: '#f59e0b',
  QUOTATION_PENDING: '#f59e0b',
  QUOTATION_SENT: '#3b82f6',
  QUOTATION_ACCEPTED: '#10b981',
  QUOTATION_REJECTED: '#ef4444',
  AWAITING_PAYMENT: '#f97316',
  CONFIRMED: '#3b82f6',
  ASSIGNED: '#8b5cf6',
  IN_PROGRESS: '#8b5cf6',
  COMPLETED: '#10b981',
  CANCELLED: '#ef4444',
  REFUNDED: '#64748b',
  DISPUTED: '#f97316',
};

export const PAYMENT_STATUS_COLORS: Record<string, string> = {
  PAID: '#10b981',
  PENDING: '#f59e0b',
  REFUNDED: '#64748b',
  FAILED: '#ef4444',
};

/** Account statuses from `auth-service`. A different vocabulary from bookings — kept separate. */
export const USER_STATUS_COLORS: Record<string, string> = {
  ACTIVE: '#10b981',
  PENDING: '#f59e0b',
  PENDING_VERIFICATION: '#f59e0b',
  SUSPENDED: '#ef4444',
  BANNED: '#dc2626',
  INACTIVE: NEUTRAL_STATUS_COLOR,
};

const lookup = (map: Record<string, string>, status?: string | null): string =>
  (status && map[status]) || NEUTRAL_STATUS_COLOR;

export const bookingStatusColor = (status?: string | null): string =>
  lookup(BOOKING_STATUS_COLORS, status);

export const paymentStatusColor = (status?: string | null): string =>
  lookup(PAYMENT_STATUS_COLORS, status);

export const userStatusColor = (status?: string | null): string =>
  lookup(USER_STATUS_COLORS, status);

/**
 * The tinted-chip pair these screens all build by hand: a 15/255 wash of the colour behind the
 * colour itself. Written out in six places with the same `${color}15` suffix trick.
 */
export const statusChipSx = (color: string) => ({
  bgcolor: `${color}15`,
  color,
  fontWeight: 600,
});
