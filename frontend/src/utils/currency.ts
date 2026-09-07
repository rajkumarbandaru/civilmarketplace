/**
 * Rupee rendering for the whole app.
 *
 * There were five of these — `RevenuePage`, `AdminDashboard`, `BookingManagement`, `InvoicesPage`
 * and `bookingPricing`'s `formatRupees` — and they did not agree. Two mattered:
 *
 * - `AdminDashboard`'s compact form stopped at lakh, `RevenuePage`'s went to crore. The same
 *   ₹1,20,00,000 read as "₹120.0L" on the dashboard and "₹1.20Cr" on the revenue screen.
 * - Three of the five used the browser's default locale rather than `en-IN`, so the grouping came
 *   out as 12,000,000 instead of the 1,20,00,000 an Indian reader expects.
 *
 * Both forms below use `en-IN` explicitly. The lakh/crore grouping is the point of this app's
 * numbers and must not depend on the reader's browser settings.
 */

/** Amounts arrive from the API as numbers, but a missing figure comes through as null. */
export type Amount = number | null | undefined;

const toNumber = (value: Amount): number =>
  typeof value === 'number' && Number.isFinite(value) ? value : 0;

/**
 * The full figure — "₹1,20,00,000". Use wherever the exact amount matters: invoices, ledger lines,
 * anything a user might reconcile against a bank statement.
 *
 * Paise are shown only when there are any, so whole rupees do not render a pointless ".00".
 */
export const formatCurrency = (value: Amount): string =>
  `₹${toNumber(value).toLocaleString('en-IN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })}`;

/**
 * The abbreviated figure — "₹1.20Cr", "₹1.5L", "₹12.3K". Use only in stat tiles and chart labels,
 * where the trend is the point and the exact rupee is not.
 *
 * Never use this on an invoice or a wallet balance: "₹1.2L" is not a number anyone can check.
 */
export const formatCompactCurrency = (value: Amount): string => {
  const amount = toNumber(value);
  const sign = amount < 0 ? '-' : '';
  const magnitude = Math.abs(amount);

  if (magnitude >= 10000000) return `${sign}₹${(magnitude / 10000000).toFixed(2)}Cr`;
  if (magnitude >= 100000) return `${sign}₹${(magnitude / 100000).toFixed(1)}L`;
  if (magnitude >= 1000) return `${sign}₹${(magnitude / 1000).toFixed(1)}K`;
  return `${sign}${formatCurrency(magnitude)}`;
};

/** Plain counts — "1,20,000". Same grouping rule, no symbol. */
export const formatCount = (value: Amount): string =>
  toNumber(value).toLocaleString('en-IN');
