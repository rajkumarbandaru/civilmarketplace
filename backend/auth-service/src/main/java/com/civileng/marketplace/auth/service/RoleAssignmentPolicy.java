package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.web.common.PlatformRoles;
import com.civileng.marketplace.web.common.StaffRoles;

import java.util.Collection;
import java.util.List;

/**
 * Which roles can be given to someone, in which tenant, by whom.
 *
 * <p>Every tenant's auth schema seeds the same roles table, so the two staff tiers exist in every
 * schema as rows. This is what keeps them apart as assignments:
 * <ul>
 *   <li>Platform roles ({@code PLATFORM_*}) are only assignable in the operator tenant — platform staff
 *       never live inside a customer's workspace.</li>
 *   <li>Nothing else is assignable in the operator tenant: not a tenant's own staff roles
 *       ({@code TENANT_OWNER} and the admins below it), and not the member roles (customer,
 *       engineer, supplier, ...). The platform console has no business of its own, so it has no
 *       members — only platform staff.</li>
 *   <li>An owner role is only given by an owner.</li>
 * </ul>
 *
 * <p>Static and tenant-from-context so every path that sets a role — invitation, admin edit,
 * owner provisioning — asks the same question the same way.
 */
public final class RoleAssignmentPolicy {

    private RoleAssignmentPolicy() {
    }

    /** True when {@code role} may exist in {@code tenantId} at all. */
    public static boolean assignableIn(String tenantId, String role) {
        if (role == null) {
            return false;
        }
        // Exactly one tier per tenant: platform roles on the operator tenant, every other role
        // everywhere else.
        return PlatformRoles.isPlatformRole(role) == PlatformRoles.isOperatorTenant(tenantId);
    }

    /**
     * Throws {@link SecurityException} (403) unless {@code actorRole} may give {@code role} to someone
     * in the tenant the current request is on.
     */
    public static void check(String role, String actorRole) {
        String tenant = TenantContext.get();
        if (!assignableIn(tenant, role)) {
            throw new SecurityException(PlatformRoles.isPlatformRole(role)
                    ? "Platform roles can only be given on the platform console"
                    : "The platform console only has platform roles, not " + role);
        }
        if (StaffRoles.isOwner(role) && !StaffRoles.isOwner(actorRole)) {
            throw new SecurityException("Only the workspace owner can add another owner");
        }
    }

    /** The subset of {@code roles} that exist as choices in {@code tenantId}. */
    public static List<String> visibleIn(String tenantId, Collection<String> roles) {
        return roles.stream().filter(role -> assignableIn(tenantId, role)).toList();
    }

    /** The owner role new workspaces are given: the platform owner on the operator tenant, else the tenant owner. */
    public static String ownerRoleFor(String tenantId) {
        return StaffRoles.ownerRoleFor(tenantId);
    }
}
