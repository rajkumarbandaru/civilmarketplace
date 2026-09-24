import { describe, expect, it } from 'vitest';
import { formatCompactCurrency, formatCount, formatCurrency } from './currency';

describe('formatCurrency', () => {
  it('groups in lakh/crore regardless of browser locale', () => {
    expect(formatCurrency(12000000)).toBe('₹1,20,00,000');
  });

  it('shows paise only when present', () => {
    expect(formatCurrency(1500)).toBe('₹1,500');
    expect(formatCurrency(1500.5)).toBe('₹1,500.5');
    expect(formatCurrency(1500.256)).toBe('₹1,500.26');
  });

  it('treats missing and non-finite values as zero', () => {
    expect(formatCurrency(null)).toBe('₹0');
    expect(formatCurrency(undefined)).toBe('₹0');
    expect(formatCurrency(Number.NaN)).toBe('₹0');
  });
});

describe('formatCompactCurrency', () => {
  it.each([
    [12000000, '₹1.20Cr'],
    [150000, '₹1.5L'],
    [12300, '₹12.3K'],
    [999, '₹999'],
    [-150000, '-₹1.5L'],
  ])('%d → %s', (value, expected) => {
    expect(formatCompactCurrency(value)).toBe(expected);
  });
});

describe('formatCount', () => {
  it('uses Indian grouping without a symbol', () => {
    expect(formatCount(120000)).toBe('1,20,000');
    expect(formatCount(null)).toBe('0');
  });
});
