import { describe, expect, it } from 'vitest';
import { landingPathFor } from './AdminRoute';

describe('landingPathFor', () => {
  it.each(['TENANT_OWNER', 'ADMIN', 'SUB_ADMIN', 'PLATFORM_OWNER', 'PLATFORM_ADMIN', 'PLATFORM_SUPPORT'])('sends %s to the console', (role) => {
    expect(landingPathFor(role)).toBe('/admin');
  });

  it.each(['CUSTOMER', 'PROFESSIONAL', 'SUPER_ADMIN', null, undefined])('sends %s to the dashboard', (role) => {
    expect(landingPathFor(role)).toBe('/dashboard');
  });
});
