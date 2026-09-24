import { describe, expect, it } from 'vitest';
import { landingPathFor } from './AdminRoute';

describe('landingPathFor', () => {
  it.each(['SUPER_ADMIN', 'ADMIN', 'SUB_ADMIN'])('sends %s to the console', (role) => {
    expect(landingPathFor(role)).toBe('/admin');
  });

  it.each(['CUSTOMER', 'PROFESSIONAL', null, undefined])('sends %s to the dashboard', (role) => {
    expect(landingPathFor(role)).toBe('/dashboard');
  });
});
