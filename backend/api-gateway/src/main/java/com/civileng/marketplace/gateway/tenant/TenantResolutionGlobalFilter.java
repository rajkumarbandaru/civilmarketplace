package com.civileng.marketplace.gateway.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Establishes which tenant every request belongs to, before authentication runs.
 *
 * <p>Three things happen here, in order:
 * <ol>
 *   <li>Any client-supplied {@code X-Tenant-Id} is stripped. Downstream services trust that header
 *       absolutely, so letting a caller set it would be a one-header cross-tenant read.</li>
 *   <li>The host is resolved to a tenant, and a non-ACTIVE tenant is refused here rather than
 *       eleven services later.</li>
 *   <li>The route's module is checked against the tenant's enabled set, so a fee-collection tenant
 *       gets 404 on {@code /api/v1/bookings/**} instead of an empty list from a schema that
 *       happens to exist.</li>
 * </ol>
 *
 * <p>The JWT's own tenant claim is cross-checked separately, in {@code JwtAuthGatewayFilterFactory}
 * — a valid token from tenant A must not work against tenant B's host.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String TENANT_ATTRIBUTE = "platform.tenant";

    /**
     * Path prefix → the module that must be enabled to reach it. Anything unlisted is horizontal
     * and always available.
     */
    private static final Map<String, String> MODULE_BY_PATH_PREFIX = Map.ofEntries(
            Map.entry("/api/v1/bookings", "bookings"),
            Map.entry("/api/v1/catalogue", "bookings"),
            Map.entry("/api/v1/projects", "projects"),
            Map.entry("/api/v1/reviews", "reviews"),
            Map.entry("/api/v1/search", "search"),
            Map.entry("/api/v1/residents", "residents"),
            Map.entry("/api/v1/fee-plans", "feeplans"),
            Map.entry("/api/v1/invoices", "invoices"),
            Map.entry("/api/v1/collections", "collections"),
            Map.entry("/api/v1/properties", "properties"),
            Map.entry("/api/v1/listings", "listings"),
            Map.entry("/api/v1/leases", "leases"),
            Map.entry("/api/v1/valuations", "valuations"),
            Map.entry("/api/v1/land-records", "landrecords"),
            // Tenant administration is itself a module, held by the operator tenant alone — so a
            // customer tenant is refused here rather than by tenant-service's own role check.
            Map.entry("/api/v1/tenants", "tenantadmin"));

    private final TenantDirectory tenantDirectory;

    /**
     * Local development has no wildcard DNS: {@code localhost} resolves to no tenant, so requests
     * fall back to this one. Must be blank in any deployed environment — with it set, an
     * unrecognised Host silently becomes a real tenant's traffic.
     */
    @Value("${platform.tenant.fallback:}")
    private String fallbackTenant;

    @Override
    public int getOrder() {
        // Ahead of the JWT filter, which reads the resolved tenant back to check the token claim.
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/actuator") || path.startsWith("/api/v1/tenant-resolution")) {
            return chain.filter(exchange);
        }

        String host = hostWithoutPort(exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));

        return tenantDirectory.resolve(host)
                .switchIfEmpty(fallback())
                // Wrap before flatMap: chain.filter() completes empty on every proxied request, so a
                // switchIfEmpty placed after it would fire on success and try to write a rejection
                // into an already-committed response.
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(resolved -> {
                    if (resolved.isEmpty()) {
                        return reject(exchange, HttpStatus.NOT_FOUND,
                                "No workspace is served at " + host);
                    }
                    TenantDescriptor tenant = resolved.get();
                    if (!tenant.isActive()) {
                        return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "This workspace is " + tenant.getStatus().toLowerCase());
                    }

                    String requiredModule = requiredModuleFor(path);
                    if (requiredModule != null && !tenant.hasModule(requiredModule)) {
                        log.debug("Tenant '{}' has no '{}' module; refusing {}",
                                tenant.getTenantKey(), requiredModule, path);
                        return reject(exchange, HttpStatus.NOT_FOUND, "Not found");
                    }

                    exchange.getAttributes().put(TENANT_ATTRIBUTE, tenant);
                    ServerWebExchange tenanted = exchange.mutate()
                            .request(r -> r
                                    .headers(headers -> headers.remove(TENANT_HEADER))
                                    .header(TENANT_HEADER, tenant.getTenantKey()))
                            .build();
                    return chain.filter(tenanted);
                });
    }

    /**
     * Tenants are keyed by hostname; a local {@code :8080} or {@code :3000} is not part of the
     * identity and would turn every lookup into a miss.
     */
    private String hostWithoutPort(String host) {
        if (host == null) {
            return "";
        }
        int colon = host.lastIndexOf(':');
        return colon < 0 ? host : host.substring(0, colon);
    }

    private Mono<TenantDescriptor> fallback() {
        if (fallbackTenant == null || fallbackTenant.isBlank()) {
            return Mono.empty();
        }
        return tenantDirectory.resolve(fallbackTenant);
    }

    private String requiredModuleFor(String path) {
        return MODULE_BY_PATH_PREFIX.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        if (exchange.getResponse().isCommitted()) {
            // Nothing useful left to say — writing headers now would blow up mid-body and truncate
            // the response the client is already reading.
            log.warn("Refusing '{}' after response was committed", exchange.getRequest().getURI());
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = String.format(
                "{\"success\":false,\"message\":\"%s\",\"status\":%d}", message, status.value());
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }
}
