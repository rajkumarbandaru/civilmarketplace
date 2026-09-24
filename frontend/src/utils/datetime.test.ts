import { describe, expect, it } from 'vitest';
import { describeTimezone, formatDate, formatDateTime, formatTime } from './datetime';

const ts = '2026-09-24T20:15:00Z';

describe('formatDate', () => {
  it.each([
    ['DD/MM/YYYY', '24/09/2026'],
    ['MM/DD/YYYY', '09/24/2026'],
    ['YYYY-MM-DD', '2026-09-24'],
    ['D MMM YYYY', '24 Sept 2026'],
    ['MMM D, YYYY', 'Sep 24, 2026'],
  ])('%s', (dateFormat, expected) => {
    // en-GB spells September as "Sep" or "Sept" depending on the ICU version.
    const out = formatDate(ts, { timezone: 'UTC', dateFormat });
    expect(out.replace('Sept', 'Sep')).toBe(expected.replace('Sept', 'Sep'));
  });

  it('converts into the chosen zone, crossing midnight', () => {
    expect(formatDate(ts, { timezone: 'Asia/Kolkata', dateFormat: 'YYYY-MM-DD' })).toBe('2026-09-25');
  });

  it('falls back to the default format for an unknown key', () => {
    expect(formatDate(ts, { timezone: 'UTC', dateFormat: 'bogus' })).toBe('24/09/2026');
  });

  it('renders a dash for missing or invalid values', () => {
    expect(formatDate(null, { timezone: 'UTC', dateFormat: null })).toBe('—');
    expect(formatDate('nope', { timezone: 'UTC', dateFormat: null })).toBe('—');
  });

  it('survives a zone the browser does not know', () => {
    expect(() => formatDate(ts, { timezone: 'Mars/Olympus', dateFormat: null })).not.toThrow();
  });
});

describe('formatTime / formatDateTime', () => {
  it('uses 24-hour time in the chosen zone', () => {
    expect(formatTime(ts, { timezone: 'UTC', dateFormat: null })).toBe('20:15');
    expect(formatTime(ts, { timezone: 'Asia/Kolkata', dateFormat: null })).toBe('01:45');
  });

  it('joins date and time', () => {
    expect(formatDateTime(ts, { timezone: 'UTC', dateFormat: 'YYYY-MM-DD' })).toBe('2026-09-24, 20:15');
  });
});

describe('describeTimezone', () => {
  it('humanises the zone id and shows its offset', () => {
    expect(describeTimezone('Asia/Kolkata')).toBe('Asia/Kolkata (GMT+5:30)');
    expect(describeTimezone('America/New_York', new Date('2026-01-01'))).toBe('America/New York (GMT-5)');
  });
});
