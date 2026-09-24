package com.civileng.marketplace.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Removes every identity header a client sent, on every route, before anything else runs.
 *
 * <p>Services trust {@code X-User-Id} / {@code X-User-Role} as the gateway's word. Only the JWT
 * filter may set them, from a verified token — but that filter is applied per route, and the
 * public routes (auth, catalogue, content, geo, webhooks) skip it, so without this a request to
 * one of them could arrive downstream carrying {@code X-User-Role: SUPER_ADMIN} it made up.
 *
 * <p>Matched by prefix, case-insensitively, so a new {@code X-User-*} header is covered without
 * being listed here. {@code X-Tenant-Id} is handled by TenantResolutionGlobalFilter, which replaces
 * it with the tenant it resolved.
 */
@Component
@Slf4j
public class IdentityHeaderStripFilter implements GlobalFilter, Ordered {

    static final List<String> STRIPPED_PREFIXES = List.of("x-user-", "x-internal-");

    @Override
    public int getOrder() {
        // Before tenant resolution (HIGHEST_PRECEDENCE + 5) and every route filter.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        List<String> forged = headers.keySet().stream().filter(IdentityHeaderStripFilter::isIdentity).toList();
        if (forged.isEmpty()) {
            return chain.filter(exchange);
        }
        log.warn("Dropped client-supplied identity headers {} on {}", forged,
                exchange.getRequest().getPath().value());
        return chain.filter(exchange.mutate()
                .request(r -> r.headers(h -> forged.forEach(h::remove)))
                .build());
    }

    static boolean isIdentity(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return STRIPPED_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
