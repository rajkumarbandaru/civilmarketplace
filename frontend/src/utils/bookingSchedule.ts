/**
 * When a booking of each type may be scheduled for.
 *
 * A SCHEDULED booking reserves a professional's day, and that is arranged the day before at the
 * earliest — so its calendar starts tomorrow. Same-day work has its own two types: INSTANT
 * dispatches whoever is free now, and EMERGENCY jumps the queue. Letting someone pick today under
 * "Scheduled" produces a booking nobody is organised to staff, which is discovered by the customer
 * waiting for a professional who was never dispatched.
 */

/** Local `YYYY-MM-DDTHH:mm`, the only format `<input type="datetime-local">` accepts for min. */
const toLocalInput = (date: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

/** Midnight at the start of the day `daysAhead` from today, in the browser's own timezone. */
const startOfDay = (daysAhead: number): Date => {
  const date = new Date();
  date.setDate(date.getDate() + daysAhead);
  date.setHours(0, 0, 0, 0);
  return date;
};

/** The earliest moment this booking type may be scheduled for. */
export const minScheduleDateTime = (bookingType: string): string =>
  bookingType === 'SCHEDULED' ? toLocalInput(startOfDay(1)) : toLocalInput(new Date());

/**
 * Whether a chosen date is allowed for this booking type.
 *
 * Enforced as well as declared: an `<input min>` is advisory — a browser that ignores it, or a
 * value pasted in, would otherwise sail through to a booking dated yesterday.
 */
export const isScheduleAllowed = (bookingType: string, value: string): boolean => {
  if (!value) return true;
  const chosen = new Date(value);
  if (Number.isNaN(chosen.getTime())) return false;
  return chosen >= new Date(minScheduleDateTime(bookingType));
};

/** What to tell the customer when their date is out of range for the type they picked. */
export const scheduleHint = (bookingType: string): string => {
  switch (bookingType) {
    case 'SCHEDULED':
      return 'Scheduled bookings start from tomorrow. For work today, choose Instant or Emergency booking.';
    case 'EMERGENCY':
      return 'Emergency bookings are dispatched right away — pick a time from now onwards.';
    case 'QUOTATION':
      return 'Optional: when you would like the work done, once the quote is agreed.';
    default:
      return 'Instant bookings are matched with a professional available now.';
  }
};
