import { describe, expect, it } from 'vitest';
import { durationMinutes, parseRate, priceBreakdown, quantityLabel } from './bookingPricing';

describe('parseRate', () => {
  it('reads rate and unit from a catalogue price', () => {
    expect(parseRate('₹700/hr')).toEqual({ rate: 700, unit: 'hr' });
    expect(parseRate('₹2,500/day')).toEqual({ rate: 2500, unit: 'day' });
    expect(parseRate('₹ 45 / SQFT')).toEqual({ rate: 45, unit: 'sqft' });
  });

  it('returns null for anything not payable upfront', () => {
    expect(parseRate('Quote')).toBeNull();
    expect(parseRate('')).toBeNull();
    expect(parseRate(null)).toBeNull();
    expect(parseRate('₹0/hr')).toBeNull();
  });
});

describe('priceBreakdown', () => {
  it('applies 5% platform fee and 18% GST on cost plus fee', () => {
    expect(priceBreakdown(700, 2)).toEqual({
      subtotal: 1400,
      platformFee: 70,
      gst: 264.6,
      total: 1734.6,
    });
  });

  it('rounds to paise', () => {
    const { subtotal, platformFee, gst, total } = priceBreakdown(33.33, 3);
    for (const n of [subtotal, platformFee, gst, total]) {
      expect(Math.round(n * 100) / 100).toBe(n);
    }
  });
});

describe('quantityLabel / durationMinutes', () => {
  it('labels known and unknown units', () => {
    expect(quantityLabel('hr')).toBe('Number of hours');
    expect(quantityLabel('litre')).toBe('Quantity (litre)');
  });

  it('only implies a duration for time-based units', () => {
    expect(durationMinutes('hr', 1.5)).toBe(90);
    expect(durationMinutes('day', 2)).toBe(960);
    expect(durationMinutes('sqft', 100)).toBeUndefined();
  });
});
