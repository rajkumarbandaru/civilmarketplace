import { afterEach, describe, expect, it } from 'vitest';
import {
  actingTenantFor,
  getActingTenant,
  inTenantWorkspace,
  isActingEligible,
  setActingTenant,
  tenantScreenPath,
} from './actingTenant';

const civ = { tenantKey: 'civengmarket', name: 'CivEngMarket' };

describe('actingTenant', () => {
  afterEach(() => setActingTenant(null));

  it('sends the header only for staff APIs, on a tenant page, with a tenant picked', () => {
    expect(actingTenantFor('/admin/users', 'get', '/admin/tenant/users')).toBeNull();

    setActingTenant(civ);
    expect(actingTenantFor('/admin/users', 'get', '/admin/tenant/users')).toBe('civengmarket');
    expect(actingTenantFor('/admin/services/7', 'put', '/admin/tenant/services')).toBe('civengmarket');
    // The console's own screens stay on the platform even with a tenant picked.
    expect(actingTenantFor('/admin/users', 'get', '/admin/users')).toBeNull();
    // The shell's own calls never switch: menu, bell, profile.
    expect(actingTenantFor('/ui-config/me', 'get', '/admin/tenant/users')).toBeNull();
    expect(actingTenantFor('/notifications/unread-count', 'get', '/admin/tenant/users')).toBeNull();
    expect(actingTenantFor('/users/me', 'get', '/admin/tenant/users')).toBeNull();
  });

  it('mirrors the gateway: staff prefixes any method, a few extras read-only', () => {
    expect(isActingEligible('/admin', 'get')).toBe(true);
    expect(isActingEligible('/users/admin/kyc/pending', 'get')).toBe(true);
    expect(isActingEligible('/workspace-settings/modules', 'put')).toBe(true);
    expect(isActingEligible('/analytics/workspace', 'get')).toBe(true);
    expect(isActingEligible('/analytics/workspace', 'post')).toBe(false);
    expect(isActingEligible('/bookings/b1/tracking', 'get')).toBe(true);
    expect(isActingEligible('/bookings/b1/cancel', 'post')).toBe(false);
    // A segment boundary, not a bare prefix.
    expect(isActingEligible('/administrators', 'get')).toBe(false);
    // Absolute URLs and query strings are read the same way.
    expect(isActingEligible('http://host/api/v1/admin/users?page=2', 'get')).toBe(true);
  });

  it('keeps links inside the tenant workspace', () => {
    expect(tenantScreenPath('/admin/users', '/admin/tenant')).toBe('/admin/tenant/users');
    expect(tenantScreenPath('/admin/users', '/admin/bookings')).toBe('/admin/users');
    expect(tenantScreenPath('/admin/tenant/kyc', '/admin/tenant/users')).toBe('/admin/tenant/kyc');
    expect(inTenantWorkspace('/admin/tenants')).toBe(false);
    expect(inTenantWorkspace('/admin/tenant')).toBe(true);
  });

  it('remembers the pick for the tab', () => {
    setActingTenant(civ);
    expect(getActingTenant()).toEqual(civ);
    expect(JSON.parse(sessionStorage.getItem('platform.actingTenant') as string)).toEqual(civ);
    setActingTenant(null);
    expect(sessionStorage.getItem('platform.actingTenant')).toBeNull();
  });
});
