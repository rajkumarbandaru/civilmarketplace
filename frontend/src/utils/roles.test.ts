import { describe, expect, it } from 'vitest';
import {
  isOwnerRole, isPlatformOperator, isPlatformRole, isReadOnlyRole, isTenantStaffRole, roleLabel,
} from './roles';

describe('roles', () => {
  it.each(['PLATFORM_OWNER', 'PLATFORM_ADMIN', 'PLATFORM_SUPPORT'])('%s is RK platform staff', (role) => {
    expect(isPlatformRole(role)).toBe(true);
    expect(isTenantStaffRole(role)).toBe(false);
  });

  it.each(['TENANT_OWNER', 'ADMIN', 'SUB_ADMIN', 'REGIONAL_ADMIN'])('%s is a tenant\'s own staff', (role) => {
    expect(isTenantStaffRole(role)).toBe(true);
    expect(isPlatformRole(role)).toBe(false);
  });

  it('knows both owners and nobody else', () => {
    expect(isOwnerRole('TENANT_OWNER')).toBe(true);
    expect(isOwnerRole('PLATFORM_OWNER')).toBe(true);
    expect(isOwnerRole('PLATFORM_ADMIN')).toBe(false);
    expect(isOwnerRole('SUPER_ADMIN')).toBe(false);
    expect(isOwnerRole(undefined)).toBe(false);
  });

  it('only counts platform staff as the operator on the platform tenant', () => {
    expect(isPlatformOperator('PLATFORM_OWNER', 'platform')).toBe(true);
    expect(isPlatformOperator('PLATFORM_OWNER', 'civengmarket')).toBe(false);
    expect(isPlatformOperator('TENANT_OWNER', 'platform')).toBe(false);
  });

  it('marks platform support as read-only', () => {
    expect(isReadOnlyRole('PLATFORM_SUPPORT')).toBe(true);
    expect(isReadOnlyRole('PLATFORM_ADMIN')).toBe(false);
  });

  it('names the tier roles properly and humanises the rest', () => {
    expect(roleLabel('PLATFORM_OWNER')).toBe('Platform owner');
    expect(roleLabel('TENANT_OWNER')).toBe('Tenant owner');
    expect(roleLabel('SITE_ENGINEER')).toBe('Site engineer');
  });
});
