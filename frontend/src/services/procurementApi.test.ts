import { describe, expect, it } from 'vitest';
import { formatMoney, formatQty, lineTotals } from './procurementApi';

describe('procurementApi helpers', () => {
  it('totals lines the way the server does: rounded per line to paise', () => {
    // Mirrors ThreeWayMatchTest.totalsRoundPerLineToPaise in procurement-service.
    expect(lineTotals([
      { quantity: 3, unitPrice: 33.33, taxPercent: 18 },
      { quantity: 0.5, unitPrice: 0.99, taxPercent: 5 },
    ])).toEqual({ subtotal: 100.49, tax: 18.03, total: 118.52 });
    expect(lineTotals([])).toEqual({ subtotal: 0, tax: 0, total: 0 });
  });

  it('formats rupees in Indian grouping and quantities without trailing zeros', () => {
    expect(formatMoney(409400)).toBe('₹4,09,400.00');
    expect(formatMoney(null)).toBe('—');
    expect(formatQty(500)).toBe('500');
    expect(formatQty(2.5)).toBe('2.5');
  });
});
