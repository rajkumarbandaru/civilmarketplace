import { describe, expect, it } from 'vitest';
import { isNoopOverride, MenuCatalogueEntry, overrideRoles, previewMenu, tenantRoleOptions } from './tenantApi';

const entry = (itemKey: string, defaultRoles: string): MenuCatalogueEntry => ({
  itemKey, label: itemKey, path: `/${itemKey}`, icon: 'Circle', section: 'Main', menuGroup: null,
  sortOrder: 1, requiredModule: null, defaultRoles,
});

describe('tenant menu role ceilings', () => {
  it('offers the catalogue roles and tenant staff, never platform roles or the wildcard', () => {
    const options = tenantRoleOptions([
      entry('bookings', '*'),
      entry('procurement', 'MATERIAL_SUPPLIER,CIVIL_ENGINEER'),
      entry('tenants', 'PLATFORM_OWNER,PLATFORM_ADMIN'),
    ]);
    expect(options).toContain('MATERIAL_SUPPLIER');
    expect(options).toContain('TENANT_OWNER');
    expect(options).not.toContain('*');
    expect(options.some((r) => r.startsWith('PLATFORM_'))).toBe(false);
  });

  it('a row that only restricts roles is kept', () => {
    expect(isNoopOverride({ itemKey: 'procurement', visible: true, roles: 'CUSTOMER' })).toBe(false);
    expect(isNoopOverride({ itemKey: 'procurement', visible: true, roles: null })).toBe(true);
  });

  it('the preview shows the ceiling as the item\'s roles', () => {
    const [item] = previewMenu([entry('procurement', '*')], [], [{ itemKey: 'procurement', roles: 'CUSTOMER,WORKER' }]);
    expect(item.defaultRoles).toBe('CUSTOMER,WORKER');
    expect(overrideRoles({ itemKey: 'procurement', roles: 'CUSTOMER, WORKER' })).toEqual(['CUSTOMER', 'WORKER']);
    expect(overrideRoles(undefined)).toEqual([]);
  });
});
