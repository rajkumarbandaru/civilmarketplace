package com.civileng.marketplace.web.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * The platform operator's roles — The platform company's own staff, who run the platform factory and every
 * tenant on it — kept apart from the roles a tenant uses to run its own business.
 *
 * <p>Two conditions, always together. The role says who someone is; the tenant says where the
 * account lives. Platform roles are only ever assigned inside the operator tenant, but a gate that
 * checked the role alone would make that an assumption rather than a rule — a customer tenant's
 * roles table that somehow held a {@code PLATFORM_OWNER} row would otherwise hand its holder the
 * factory. Checking both means neither the roles table nor the tenant header is trusted alone.
 *
 * <ul>
 *   <li>{@link #OWNER} — full control of the platform, including its own staff.</li>
 *   <li>{@link #ADMIN} — runs tenants day to day: onboarding, plans, domains, integrations.</li>
 *   <li>{@link #SUPPORT} — sees everything the console shows, changes nothing.</li>
 * </ul>
 */
public final class PlatformRoles {

    /** The tenant that is the platform itself. Its key doubles as every service's bootstrap schema. */
    public static final String OPERATOR_TENANT = "platform";

    public static final String OWNER = "PLATFORM_OWNER";
    public static final String ADMIN = "PLATFORM_ADMIN";
    public static final String SUPPORT = "PLATFORM_SUPPORT";

    public static final Set<String> ALL = Set.of(OWNER, ADMIN, SUPPORT);

    /** The platform roles that may change tenants, not just look at them. */
    public static final Set<String> MANAGERS = Set.of(OWNER, ADMIN);

    private PlatformRoles() {
    }

    public static boolean isPlatformRole(String role) {
        return role != null && ALL.contains(role);
    }

    public static boolean isOperatorTenant(String tenantId) {
        return OPERATOR_TENANT.equals(tenantId);
    }

    /** Read-only platform staff: allowed to look, never to write. */
    public static boolean isReadOnly(String role) {
        return SUPPORT.equals(role);
    }

    /** Any platform staff member, signed in on the operator tenant. */
    public static boolean canView(String tenantId, String role) {
        return isOperatorTenant(tenantId) && isPlatformRole(role);
    }

    /** A platform owner or admin, signed in on the operator tenant. */
    public static boolean canManage(String tenantId, String role) {
        return isOperatorTenant(tenantId) && role != null && MANAGERS.contains(role);
    }

    /** Throws {@link AccessDeniedException} unless the caller is platform staff on the operator tenant. */
    public static void requireView(String tenantId, String role, String what) {
        if (!canView(tenantId, role)) {
            throw new AccessDeniedException(what + " is restricted to platform staff");
        }
    }

    /** Throws {@link AccessDeniedException} unless the caller is a platform owner or admin on the operator tenant. */
    public static void requireManage(String tenantId, String role, String what) {
        if (!canManage(tenantId, role)) {
            throw new AccessDeniedException(what + " is restricted to platform owners and admins");
        }
    }

    /** {@link #requireView} for reads, {@link #requireManage} for anything else. */
    public static void require(String tenantId, String role, boolean write, String what) {
        if (write) {
            requireManage(tenantId, role, what);
        } else {
            requireView(tenantId, role, what);
        }
    }

    /**
     * The gate for an operator endpoint, deciding read or write from the request being served: a
     * GET needs any platform staff, everything else a platform owner or admin. One call per handler
     * instead of a choice per handler, so a new read endpoint cannot be left owner-only by accident
     * nor a new write endpoint left open to read-only support staff.
     *
     * <p>Off a request thread there is no method to read, and that is treated as a write.
     */
    public static void requireOperator(String tenantId, String role, String what) {
        require(tenantId, role, !isReadRequest(), what);
    }

    /** Irreversible platform actions — destroying a tenant's keys — are for the platform owner alone. */
    public static void requireOwner(String tenantId, String role, String what) {
        if (!isOperatorTenant(tenantId) || !OWNER.equals(role)) {
            throw new AccessDeniedException(what + " is restricted to the platform owner");
        }
    }

    private static boolean isReadRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        String method = attributes.getRequest().getMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
