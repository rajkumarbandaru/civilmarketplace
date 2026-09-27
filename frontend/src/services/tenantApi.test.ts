import { describe, expect, it } from 'vitest';
import { moduleSummary } from './tenantApi';

describe('moduleSummary', () => {
  it('leaves out the internal console module the features screen never offers', () => {
    const operator = 'auth,users,payments,notifications,support,admin,audit,messaging,tenantadmin'.split(',');
    const summary = moduleSummary('CIVIL_MARKETPLACE', operator);
    expect(summary.count).toBe(8);
    expect(summary.fromOtherProducts).toEqual([]);
  });

  it("names the modules borrowed from another product's vertical", () => {
    const summary = moduleSummary('CIVIL_MARKETPLACE', ['auth', 'bookings', 'procurement', 'leases', 'residents']);
    expect(summary.count).toBe(5);
    expect(summary.fromOtherProducts).toEqual(['leases', 'residents']);
  });

  it('counts a module two products share as the tenant\'s own', () => {
    // `search` and `reviews` belong to both the marketplace and property products.
    expect(moduleSummary('PROPERTY', ['search', 'reviews', 'bookings']).fromOtherProducts).toEqual(['bookings']);
  });
});
