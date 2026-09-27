package com.civileng.marketplace.web.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Platform staff acting on a customer tenant from the platform console.
 *
 * <p>The gateway sets {@link #HEADER} — naming the tenant the caller's account lives in, always the
 * operator tenant — only after it has checked the token: a platform role, issued on the operator
 * tenant, asking for an active tenant, and read-only for platform support. The header is part of
 * the signed identity, so a service can believe it the way it believes {@code X-User-Role}.
 *
 * <p>While acting, {@code X-Tenant-Id} is the customer tenant and {@code X-User-Id} is still the
 * staff member's id <em>in the operator tenant</em>. That id names nobody in the customer tenant's
 * schema, which is why the gateway only lets acting requests reach the staff screens' APIs, never
 * the member-facing "me" endpoints.
 */
public final class ActingTenant {

    public static final String HEADER = "X-User-Acting-From";

    private ActingTenant() {
    }

    /** True if {@code actingFrom} is the operator tenant — the only place acting can start. */
    public static boolean isActing(String actingFrom) {
        return PlatformRoles.isOperatorTenant(actingFrom);
    }

    /** {@link #isActing(String)} for the request being served; false off a request thread. */
    public static boolean isActing() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        return isActing(attributes.getRequest().getHeader(HEADER));
    }
}
