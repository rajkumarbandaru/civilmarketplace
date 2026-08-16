/**
 * Turning a catalogue price into an amount to charge.
 *
 * Catalogue prices are display strings with no single basis — "₹700/hr", "₹2,500/day", "₹45/sqft",
 * "₹380/bag", and for 34 of the 116 entries simply "Quote". So a booking is payable upfront only
 * when the price names a rate *and* the customer has said how much of it they want; everything
 * else has to go through a quotation, which is what the backend already does by putting non-INSTANT
 * bookings into QUOTATION_PENDING.
 *
 * The fee maths mirrors BookingService (5% platform fee, 18% GST on cost+fee). It is duplicated
 * here only to show a breakdown *before* the booking exists; the amount actually charged is the
 * `totalAmount` the server computes and returns, never this estimate.
 */

export const PLATFORM_FEE_PERCENT = 5;
export const GST_PERCENT = 18;

export interface ParsedRate {
  /** Rupees per unit. */
  rate: number;
  /** "hr", "day", "sqft", "bag"… as written in the catalogue. */
  unit: string;
}

/**
 * Reads "₹700/hr" into { rate: 700, unit: 'hr' }.
 *
 * Returns null for "Quote", for a blank price, and for anything without a parseable number —
 * which the caller must treat as "not payable upfront" rather than as zero.
 */
export const parseRate = (price?: string | null): ParsedRate | null => {
  if (!price) return null;
  const match = price.match(/₹\s*([\d,]+(?:\.\d+)?)\s*\/\s*([A-Za-z]+)/);
  if (!match) return null;
  const rate = Number(match[1].replace(/,/g, ''));
  if (!Number.isFinite(rate) || rate <= 0) return null;
  return { rate, unit: match[2].toLowerCase() };
};

/** How the quantity is labelled for a given unit, so the field reads as the trade does. */
export const quantityLabel = (unit: string): string => {
  switch (unit) {
    case 'hr':
      return 'Number of hours';
    case 'day':
      return 'Number of days';
    case 'visit':
      return 'Number of visits';
    case 'trip':
      return 'Number of trips';
    case 'sqft':
      return 'Area (sq ft)';
    case 'bag':
      return 'Number of bags';
    case 'ton':
      return 'Weight (tons)';
    case 'kg':
      return 'Weight (kg)';
    case 'piece':
      return 'Number of pieces';
    default:
      return `Quantity (${unit})`;
  }
};

export interface PriceBreakdown {
  subtotal: number;
  platformFee: number;
  gst: number;
  total: number;
}

/** The same arithmetic the backend applies, for the pre-booking summary only. */
export const priceBreakdown = (rate: number, quantity: number): PriceBreakdown => {
  const subtotal = round2(rate * quantity);
  const platformFee = round2((subtotal * PLATFORM_FEE_PERCENT) / 100);
  const gst = round2(((subtotal + platformFee) * GST_PERCENT) / 100);
  return { subtotal, platformFee, gst, total: round2(subtotal + platformFee + gst) };
};

const round2 = (value: number): number => Math.round(value * 100) / 100;

export const formatRupees = (value: number): string =>
  `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;

/**
 * Minutes to send as the booking's estimated duration.
 *
 * Only hour- and day-rated work has a duration the quantity implies; for ₹/sqft or ₹/bag the
 * quantity says nothing about how long the job takes, so nothing is claimed.
 */
export const durationMinutes = (unit: string, quantity: number): number | undefined => {
  if (unit === 'hr') return Math.round(quantity * 60);
  if (unit === 'day') return Math.round(quantity * 8 * 60);
  return undefined;
};
