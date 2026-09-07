package com.civileng.marketplace.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Refuses, from outside, the admin surfaces that exist only so admin-service can call them.
 *
 * <p>Every admin operation used to be reachable two ways: through admin-service's console API at
 * {@code /api/v1/admin/**}, and directly on the owning service — {@code /api/v1/bookings/admin/**},
 * {@code /api/v1/auth/admin/**}, {@code /api/v1/payments/admin/**}. The console has always used
 * the first; the second was a second public door onto the same operations, and for booking-service
 * a door that went straight to the repository with no role check behind it.
 *
 * <p>These paths are not deleted, because admin-service reaches them over Feign and that call is
 * the whole reason they exist. They are simply no longer routable from the edge, which leaves one
 * externally reachable entry point per operation — the one that carries the audit trail and, since
 * the staff-role gate, an authorisation check.
 *
 * <p>404 rather than 403: whether an internal endpoint exists is not something an outside caller
 * has any business learning, and a 403 would say it does.
 */
@Component
@Slf4j
public class InternalOnlyPathFilter implements GlobalFilter, Ordered {

    /**
     * Prefixes reachable only service-to-service. Matched on a segment boundary — see
     * {@link #isUnder(String, String)} — so a future {@code /api/v1/bookings/administrators} is
     * unaffected.
     */
    private static final List<String> INTERNAL_ONLY = List.of(
            "/api/v1/bookings/admin",
            "/api/v1/auth/admin",
            "/api/v1/payments/admin",
            // user-service only. NOT the whole /api/v1/users/admin prefix: KYC review lives at
            // /api/v1/users/admin/kyc and has no console proxy in front of it, so blocking the
            // prefix wholesale would leave no way to approve a KYC document at all. Only these two
            // are duplicated by admin-service.
            "/api/v1/users/admin/profiles",
            "/api/v1/users/admin/stats"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        for (String prefix : INTERNAL_ONLY) {
            if (isUnder(path, prefix)) {
                log.warn("Refused external call to internal-only path {}", path);
                return notFound(exchange);
            }
        }
        return chain.filter(exchange);
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private Mono<Void> notFound(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = "{\"success\":false,\"message\":\"Not found\",\"status\":404}";
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        // Before tenant resolution and the JWT filter: a path that is not served from the edge at
        // all should not first be told its token is bad, and should cost nothing to refuse.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
