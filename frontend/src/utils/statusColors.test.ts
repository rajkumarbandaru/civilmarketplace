import { describe, expect, it } from 'vitest';
import {
  BOOKING_STATUS_COLORS,
  NEUTRAL_STATUS_COLOR,
  bookingStatusColor,
  paymentStatusColor,
  statusChipSx,
  userStatusColor,
} from './statusColors';

describe('status colours', () => {
  it('knows every booking status', () => {
    for (const [status, color] of Object.entries(BOOKING_STATUS_COLORS)) {
      expect(bookingStatusColor(status)).toBe(color);
    }
  });

  it('falls back to neutral for unknown or missing statuses', () => {
    expect(bookingStatusColor('SOMETHING_NEW')).toBe(NEUTRAL_STATUS_COLOR);
    expect(paymentStatusColor(null)).toBe(NEUTRAL_STATUS_COLOR);
    expect(userStatusColor(undefined)).toBe(NEUTRAL_STATUS_COLOR);
  });

  it('keeps booking and account vocabularies separate', () => {
    expect(userStatusColor('SUSPENDED')).toBe('#ef4444');
    expect(bookingStatusColor('SUSPENDED')).toBe(NEUTRAL_STATUS_COLOR);
  });

  it('builds a tinted chip style', () => {
    expect(statusChipSx('#10b981')).toEqual({ bgcolor: '#10b98115', color: '#10b981', fontWeight: 600 });
  });
});
