package com.civileng.marketplace.web.common;

import java.util.Set;

/**
 * The staff roles seeded by auth-service's {@code roles} table.
 *
 * <p>One definition, because the set was previously written out by hand in every controller that
 * gated on it — and a role added to the seed data would have had to be found in each of them.
 *
 * <p>Two tiers share this set. A tenant's own staff ({@link #TENANT_OWNER} down to
 * {@code REGIONAL_ADMIN}) run that tenant's business; the {@link PlatformRoles platform roles} run
 * the platform and exist only in the operator tenant. Both are "staff" to a tenant-scoped admin
 * screen, because every such screen only ever shows the caller's own tenant.
 */
public final class StaffRoles {

    /** The top role inside a tenant: owns its workspace, theme, content and staff. */
    public static final String TENANT_OWNER = "TENANT_OWNER";

    public static final Set<String> TENANT_STAFF =
            Set.of(TENANT_OWNER, "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    public static final Set<String> ALL = Set.of(
            TENANT_OWNER, "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN",
            PlatformRoles.OWNER, PlatformRoles.ADMIN, PlatformRoles.SUPPORT);

    /**
     * Whoever owns the workspace the request is on: the tenant owner in a customer tenant, the
     * platform owner in the operator tenant (whose workspace is the platform console itself).
     */
    public static final Set<String> OWNERS = Set.of(TENANT_OWNER, PlatformRoles.OWNER);

    /** Owners plus the admins directly below them — the roles that manage a workspace's staff and settings. */
    public static final Set<String> MANAGERS =
            Set.of(TENANT_OWNER, "ADMIN", PlatformRoles.OWNER, PlatformRoles.ADMIN);

    private StaffRoles() {
    }

    public static boolean isStaff(String role) {
        return role != null && ALL.contains(role);
    }

    /**
     * Whether {@code role} owns the workspace the request is on. Also true for a platform admin while
     * acting on a customer tenant ({@link ActingTenant}): the platform's owners and admins both run
     * tenants, so inside one they get its owner's screens (theme, content, templates, menus).
     */
    public static boolean isOwner(String role) {
        if (role == null) return false;
        return OWNERS.contains(role) || (PlatformRoles.ADMIN.equals(role) && ActingTenant.isActing());
    }

    public static boolean isManager(String role) {
        return role != null && MANAGERS.contains(role);
    }

    /** The owner role of {@code tenantId}'s workspace: the platform owner on the operator tenant, else the tenant owner. */
    public static String ownerRoleFor(String tenantId) {
        return PlatformRoles.isOperatorTenant(tenantId) ? PlatformRoles.OWNER : TENANT_OWNER;
    }

    /** Throws {@link AccessDeniedException} unless {@code role} is a staff role. */
    public static void requireStaff(String role) {
        if (!isStaff(role)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    /** Throws {@link AccessDeniedException} unless {@code role} owns the workspace. */
    public static void requireOwner(String role, String what) {
        if (!isOwner(role)) {
            throw new AccessDeniedException("Workspace owner role required to " + what);
        }
    }
}
