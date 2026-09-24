import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { isScheduleAllowed, minScheduleDateTime, scheduleHint } from './bookingSchedule';

describe('booking schedule', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 24, 10, 30));
  });
  afterEach(() => vi.useRealTimers());

  it('starts SCHEDULED bookings tomorrow and others now', () => {
    expect(minScheduleDateTime('SCHEDULED')).toBe('2026-09-25T00:00');
    expect(minScheduleDateTime('INSTANT')).toBe('2026-09-24T10:30');
  });

  it('rejects same-day SCHEDULED bookings', () => {
    expect(isScheduleAllowed('SCHEDULED', '2026-09-24T18:00')).toBe(false);
    expect(isScheduleAllowed('SCHEDULED', '2026-09-25T09:00')).toBe(true);
  });

  it('allows emergency bookings from now but not the past', () => {
    expect(isScheduleAllowed('EMERGENCY', '2026-09-24T11:00')).toBe(true);
    expect(isScheduleAllowed('EMERGENCY', '2026-09-23T11:00')).toBe(false);
  });

  it('treats blank as allowed and garbage as not', () => {
    expect(isScheduleAllowed('SCHEDULED', '')).toBe(true);
    expect(isScheduleAllowed('SCHEDULED', 'not a date')).toBe(false);
  });

  it('explains each booking type', () => {
    expect(scheduleHint('SCHEDULED')).toMatch(/from tomorrow/);
    expect(scheduleHint('INSTANT')).toMatch(/available now/);
  });
});
