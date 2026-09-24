import { describe, expect, it } from 'vitest';
import { FALLBACK_SERVICES, searchServices, slugify } from './serviceCatalogue';

describe('slugify', () => {
  it('builds URL-safe ids', () => {
    expect(slugify('Iron & TMT Steel Bars')).toBe('iron-and-tmt-steel-bars');
    expect(slugify('  3D Modeling! ')).toBe('3d-modeling');
  });
});

describe('searchServices', () => {
  it('returns nothing for a blank query', () => {
    expect(searchServices('   ', FALLBACK_SERVICES)).toEqual([]);
  });

  it('ranks an exact title above partial matches', () => {
    const [first] = searchServices('plumbing services', FALLBACK_SERVICES);
    expect(first.title).toBe('Plumbing Services');
  });

  it('requires every word to match, so more words narrow the result', () => {
    const broad = searchServices('design', FALLBACK_SERVICES);
    const narrow = searchServices('interior design', FALLBACK_SERVICES);
    expect(narrow.length).toBeLessThan(broad.length);
    expect(narrow.every((s) => /interior/i.test(s.title))).toBe(true);
  });

  it('matches on category and respects the limit', () => {
    expect(searchServices('survey', FALLBACK_SERVICES, 2)).toHaveLength(2);
  });

  it('uses aliases supplied on the entry', () => {
    const services = [{ slug: 'x', title: 'Excavator Hire', category: 'Equipment', icon: 'Build', price: 'Quote', rating: 4, reviews: 1, aliases: ['jcb'] }];
    expect(searchServices('jcb', services).map((s) => s.slug)).toEqual(['x']);
  });
});
