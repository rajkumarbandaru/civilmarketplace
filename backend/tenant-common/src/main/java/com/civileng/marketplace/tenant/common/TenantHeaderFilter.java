package com.civileng.marketplace.tenant.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds {@code X-Tenant-Id} to the thread for the life of the request. The gateway is the only
 * thing allowed to set that header — it resolves the tenant from the request's subdomain and
 * cross-checks it against the JWT's {@code tenant} claim — so a service trusts it the same way
 * it already trusts {@code X-User-Id}.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantHeaderFilter extends OncePerRequestFilter implements Ordered {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantProperties properties;

    /** Where tenants are and whether their writes are paused. Null in a service with no schema layer. */
    private final java.util.function.Supplier<TenantPlacements> placements;

    public TenantHeaderFilter(TenantProperties properties) {
        this(properties, () -> null);
    }

    private static final java.util.Set<String> READS = java.util.Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public int getOrder() {
        // Ahead of Spring Security: authentication may itself hit a tenant-scoped table.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"No tenant on this request\",\"status\":400}");
            log.warn("Rejected untenanted request to {}", request.getRequestURI());
            return;
        }

        // A tenant being moved or restored is read-only for a few seconds. Enforced here as well as
        // at the gateway: the gateway's cache may not know yet, and other services call in directly.
        TenantPlacements current = placements.get();
        if (current != null && !READS.contains(request.getMethod())
                && current.inMaintenance(TenantKey.normalise(tenantId))) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "5");
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"code\":\"TENANT_MAINTENANCE\",\"message\":"
                    + "\"This workspace is being maintained. Changes are paused for a moment; please try again.\",\"status\":503}");
            return;
        }

        TenantContext.set(TenantKey.normalise(tenantId));
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : properties.getUntenantedPaths()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
