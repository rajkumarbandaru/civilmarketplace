package com.civileng.marketplace.gateway.filter;

import com.civileng.marketplace.gateway.tenant.TenantResolutionGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Refuses a request that asked to act on a tenant but was never vetted by the JWT filter.
 *
 * <p>Tenant resolution accepts {@code X-Acting-Tenant} on the operator's host and checks the target
 * tenant, but only the JWT filter checks the caller and switches the request over. That filter is
 * applied per route, so on a public route (login, catalogue, content) the switch never happens —
 * and the request would otherwise go on as an operator-tenant request that had been module-checked
 * against the target. Refusing it is simpler than reasoning about what each public route would do.
 *
 * <p>Runs after the route filters and before the identity is signed.
 */
@Slf4j
@Component
public class ActingTenantGuardFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean asked = exchange.getAttribute(TenantResolutionGlobalFilter.ACTING_ATTRIBUTE) != null;
        boolean applied = Boolean.TRUE.equals(
                exchange.getAttribute(JwtAuthGatewayFilterFactory.ACTING_APPLIED_ATTRIBUTE));
        if (asked && !applied) {
            log.warn("Acting request on unauthenticated route {} refused", exchange.getRequest().getPath().value());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
            byte[] body = "{\"success\":false,\"message\":\"This is not available while acting on a tenant\",\"status\":403}"
                    .getBytes();
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }
        // Never forwarded: services learn about acting only from the signed X-User-Acting-From.
        if (exchange.getRequest().getHeaders().containsKey(TenantResolutionGlobalFilter.ACTING_HEADER)) {
            return chain.filter(exchange.mutate()
                    .request(r -> r.headers(h -> h.remove(TenantResolutionGlobalFilter.ACTING_HEADER)))
                    .build());
        }
        return chain.filter(exchange);
    }
}
