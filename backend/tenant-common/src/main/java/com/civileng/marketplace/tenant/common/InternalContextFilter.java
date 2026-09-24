package com.civileng.marketplace.tenant.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * Refuses identity it cannot trust. Every service believes {@code X-Tenant-Id} and
 * {@code X-User-*}; this makes that belief safe by requiring the gateway's (or a fellow
 * service's) signature over them. A request with none of those headers — a health probe, a
 * provider webhook — carries no identity and passes untouched; the tenant filter behind this one
 * still decides whether the path needs a tenant.
 */
@Slf4j
public class InternalContextFilter extends OncePerRequestFilter implements Ordered {

    private final byte[] key;
    private final long maxSkewSeconds;
    private final Clock clock;

    public InternalContextFilter(byte[] key, long maxSkewSeconds, Clock clock) {
        this.key = key;
        this.maxSkewSeconds = maxSkewSeconds;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        // Before the tenant filter (HIGHEST_PRECEDENCE + 10), which trusts X-Tenant-Id.
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!InternalContextSignature.carriesIdentity(request::getHeader)
                || InternalContextSignature.verify(key, request::getHeader,
                        request.getHeader(InternalContextSignature.HEADER),
                        clock.instant().getEpochSecond(), maxSkewSeconds)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected unsigned or badly signed identity headers on {} from {}",
                request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Untrusted caller identity\",\"status\":401}");
    }
}
