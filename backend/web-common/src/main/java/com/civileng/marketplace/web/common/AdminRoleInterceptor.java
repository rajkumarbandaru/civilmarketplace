package com.civileng.marketplace.web.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires a staff role on every request mapped below an admin prefix.
 *
 * <p>An interceptor rather than a {@code requireAdmin(role)} call per handler, because the
 * per-handler form is opt-in and opting in gets forgotten — which is exactly how admin-service's
 * nine oldest controllers (users, bookings, invoices, categories, dashboard, analytics, revenue,
 * reports, service catalogue) came to have no check at all, leaving user deletion and booking
 * cancellation reachable by any authenticated member.
 *
 * <p>This duplicates the gateway's own admin gate deliberately. The gateway is the outer layer;
 * every service also listens on its own port inside the Docker network, where nothing has stripped
 * a caller-supplied {@code X-User-Role}. Neither layer suffices alone — the gateway's check is
 * bypassed by reaching the port directly, and this one trusts a header only the gateway makes
 * trustworthy.
 *
 * <p>It is a floor, not a ceiling. Handlers needing SUPER_ADMIN specifically — admin-service's
 * theme and tenant screens — keep their own stricter check on top.
 */
@Slf4j
public class AdminRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String role = request.getHeader("X-User-Role");

        if (!StaffRoles.isStaff(role)) {
            log.warn("Refused {} {} for role '{}'",
                    request.getMethod(), request.getRequestURI(), role);
            throw new AccessDeniedException("Admin role required");
        }
        return true;
    }
}
