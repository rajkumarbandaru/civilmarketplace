/**
 * Date and time rendering for the whole app.
 *
 * Every date the user reads goes through here, so the timezone and format chosen in settings take
 * effect everywhere at once — every page, in every workspace — rather than only where someone
 * remembered to apply them.
 *
 * The formatting itself is `Intl.DateTimeFormat`, not a date library: it already knows every IANA
 * zone the browser ships with, and it does the conversion correctly across DST boundaries, which
 * is the part a hand-rolled offset always gets wrong.
 */

/** The layouts offered in settings. Mirrors `AppearanceSettings.DATE_FORMATS` on admin-service. */
export type DateFormatKey =
  | 'DD/MM/YYYY'
  | 'MM/DD/YYYY'
  | 'YYYY-MM-DD'
  | 'D MMM YYYY'
  | 'MMM D, YYYY';

export const DATE_FORMAT_KEYS: DateFormatKey[] = [
  'DD/MM/YYYY',
  'MM/DD/YYYY',
  'YYYY-MM-DD',
  'D MMM YYYY',
  'MMM D, YYYY',
];

/**
 * How each key is built.
 *
 * `YYYY-MM-DD` is the one case `Intl` cannot express through options alone — every locale reorders
 * the parts — so it is assembled from `formatToParts`, which still resolves the timezone properly.
 */
const DATE_OPTIONS: Record<DateFormatKey, Intl.DateTimeFormatOptions> = {
  'DD/MM/YYYY': { day: '2-digit', month: '2-digit', year: 'numeric' },
  'MM/DD/YYYY': { month: '2-digit', day: '2-digit', year: 'numeric' },
  'YYYY-MM-DD': { year: 'numeric', month: '2-digit', day: '2-digit' },
  'D MMM YYYY': { day: 'numeric', month: 'short', year: 'numeric' },
  'MMM D, YYYY': { month: 'short', day: 'numeric', year: 'numeric' },
};

/** The locale whose conventions produce each layout, so the separators come out as expected. */
const DATE_LOCALE: Record<DateFormatKey, string> = {
  'DD/MM/YYYY': 'en-GB',
  'MM/DD/YYYY': 'en-US',
  'YYYY-MM-DD': 'en-CA',
  'D MMM YYYY': 'en-GB',
  'MMM D, YYYY': 'en-US',
};

export interface DateTimePreferences {
  /** IANA zone id, or null to use whatever zone the browser is in. */
  timezone: string | null;
  /** A layout key, or null to fall back to the default below. */
  dateFormat: DateFormatKey | string | null;
}

/** What the site uses for someone who has never opened the settings screen. */
export const DEFAULT_DATE_FORMAT: DateFormatKey = 'DD/MM/YYYY';

/** The browser's own zone, used whenever no preference is stored. */
export const browserTimezone = (): string => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    // Older or locked-down engines can throw rather than return a zone; UTC is at least honest
    // about which zone it is, unlike silently rendering the server's.
    return 'UTC';
  }
};

const resolveKey = (format: DateTimePreferences['dateFormat']): DateFormatKey =>
  DATE_FORMAT_KEYS.includes(format as DateFormatKey)
    ? (format as DateFormatKey)
    : DEFAULT_DATE_FORMAT;

/**
 * Anything a timestamp arrives as. Backend rows come through as ISO strings, some already as
 * `Date`, and a few as epoch millis.
 */
export type DateInput = string | number | Date | null | undefined;

const toDate = (value: DateInput): Date | null => {
  if (value == null || value === '') return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

/**
 * @param fallback what to render when the value is missing or unparseable — an em dash reads as
 *                 "nothing here", where "Invalid Date" reads as a bug
 */
export const formatDate = (
  value: DateInput,
  prefs: DateTimePreferences,
  fallback = '—',
): string => {
  const date = toDate(value);
  if (!date) return fallback;

  const key = resolveKey(prefs.dateFormat);
  const timeZone = prefs.timezone || browserTimezone();

  try {
    if (key === 'YYYY-MM-DD') {
      const parts = new Intl.DateTimeFormat('en-CA', { ...DATE_OPTIONS[key], timeZone })
        .formatToParts(date);
      const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
      return `${part('year')}-${part('month')}-${part('day')}`;
    }
    return new Intl.DateTimeFormat(DATE_LOCALE[key], { ...DATE_OPTIONS[key], timeZone })
      .format(date);
  } catch {
    // A stored zone the browser does not know (an old preference, a renamed zone) must not take
    // the page down — fall back to the browser's own rather than throwing mid-render.
    return new Intl.DateTimeFormat(DATE_LOCALE[key], DATE_OPTIONS[key]).format(date);
  }
};

/** Time only, in the preferred zone. 24-hour, which is unambiguous for site and booking times. */
export const formatTime = (
  value: DateInput,
  prefs: DateTimePreferences,
  fallback = '—',
): string => {
  const date = toDate(value);
  if (!date) return fallback;
  const timeZone = prefs.timezone || browserTimezone();
  try {
    return new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', hour12: false, timeZone,
    }).format(date);
  } catch {
    return new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', hour12: false,
    }).format(date);
  }
};

/** Date and time together, the form most tables and activity logs want. */
export const formatDateTime = (
  value: DateInput,
  prefs: DateTimePreferences,
  fallback = '—',
): string => {
  const date = toDate(value);
  if (!date) return fallback;
  return `${formatDate(value, prefs, fallback)}, ${formatTime(value, prefs, fallback)}`;
};

/**
 * The zone as a person recognises it — "Asia/Kolkata (IST, GMT+5:30)". Used on the settings screen
 * so the choice can be confirmed at a glance rather than by trusting a zone id.
 */
export const describeTimezone = (zone: string, at: Date = new Date()): string => {
  try {
    const parts = new Intl.DateTimeFormat('en-GB', { timeZone: zone, timeZoneName: 'shortOffset' })
      .formatToParts(at);
    const offset = parts.find((p) => p.type === 'timeZoneName')?.value ?? '';
    return offset ? `${zone.replace(/_/g, ' ')} (${offset})` : zone.replace(/_/g, ' ');
  } catch {
    return zone;
  }
};

/**
 * Every zone the browser knows, for the settings picker.
 *
 * `supportedValuesOf` is the real list and is present in current browsers; the short fallback
 * covers an engine without it, so the control is never empty.
 */
export const availableTimezones = (): string[] => {
  const supported = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] })
    .supportedValuesOf;
  if (typeof supported === 'function') {
    try {
      return supported('timeZone');
    } catch {
      /* falls through to the short list */
    }
  }
  return [
    'UTC', 'Asia/Kolkata', 'Asia/Dubai', 'Asia/Singapore', 'Asia/Tokyo',
    'Europe/London', 'Europe/Berlin', 'Europe/Paris',
    'America/New_York', 'America/Chicago', 'America/Los_Angeles',
    'Australia/Sydney',
  ];
};
