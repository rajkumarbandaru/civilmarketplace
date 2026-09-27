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
 *
 * <p>One exception to "the host is the tenant": platform staff on the operator's host may ask, with
 * {@link #ACTING_HEADER}, to act on a customer tenant. The target tenant's status and modules are
 * enforced here in place of the operator's; everything about the caller is left to the JWT filter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    static final String CURRENT_TENANT_PATH = "/api/v1/tenant-resolution/current";
    public static final String TENANT_ATTRIBUTE = "platform.tenant";

    /**
     * Sent by the platform console when its staff work inside a customer tenant. Only a request
     * addressed to the operator's host may carry it; the tenant it names is checked here (exists,
     * live, has the route's module) and the caller is checked by JwtAuthGatewayFilterFactory, which
     * alone switches {@code X-Tenant-Id} over to it. See {@link #ACTING_ATTRIBUTE}.
     */
    public static final String ACTING_HEADER = "X-Acting-Tenant";

    /** The tenant an acting request asked for, once this filter has vetted it. */
    public static final String ACTING_ATTRIBUTE = "platform.actingTenant";

    public static final String OPERATOR_TENANT = "platform";

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
            Map.entry("/api/v1/procurement", "procurement"),
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

    private static final java.util.Set<org.springframework.http.HttpMethod> READS = java.util.Set.of(
            org.springframework.http.HttpMethod.GET, org.springframework.http.HttpMethod.HEAD,
            org.springframework.http.HttpMethod.OPTIONS);

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

        // The by-host lookup is answered for any host, so it needs no tenant of its own. /current
        // is the opposite: "which workspace is this address?", resolved exactly as every other
        // request is — fallback, 404 for an unknown host, 503 for a suspended one.
        if (path.startsWith("/actuator") || path.startsWith("/.well-known/acme-challenge/")
                || (path.startsWith("/api/v1/tenant-resolution") && !path.equals(CURRENT_TENANT_PATH))) {
            return chain.filter(exchange);
        }

        // Provider webhooks arrive on the platform's API host, not a tenant's, so the Host header
        // names no tenant. The service resolves the tenant from the opaque token in the path
        // instead — and must never be handed one the caller chose, so the header is dropped.
        if (path.startsWith("/webhooks/")) {
            return chain.filter(exchange.mutate()
                    .request(r -> r.headers(headers -> headers.remove(TENANT_HEADER)))
                    .build());
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
                    if (tenant.isMaintenance() && !READS.contains(exchange.getRequest().getMethod())) {
                        // Reads go on; a write would land on data that is being copied.
                        exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, "5");
                        return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "This workspace is being maintained. Changes are paused for a moment; please try again.");
                    }
                    if (!tenant.isActive() && !tenant.isMaintenance()) {
                        return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "This workspace is " + tenant.getStatus().toLowerCase());
                    }

                    String acting = exchange.getRequest().getHeaders().getFirst(ACTING_HEADER);
                    if (acting != null && !acting.isBlank()) {
                        return actOn(exchange, chain, tenant, acting.trim().toLowerCase(java.util.Locale.ROOT), path);
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

    /**
     * An operator-host request asking to act on {@code targetKey}: the target must exist, be live
     * (reads only while it is in maintenance), not be the operator itself, and have the module the
     * route needs. {@code X-Tenant-Id} stays the operator's here — the token is checked against it —
     * and the vetted target waits in {@link #ACTING_ATTRIBUTE} for the JWT filter.
     */
    private Mono<Void> actOn(ServerWebExchange exchange, GatewayFilterChain chain, TenantDescriptor host,
                             String targetKey, String path) {
        if (!OPERATOR_TENANT.equals(host.getTenantKey())) {
            log.warn("Acting header sent to tenant '{}' host, refused", host.getTenantKey());
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "Acting on a tenant is only possible from the platform console");
        }
        if (OPERATOR_TENANT.equals(targetKey)) {
            return reject(exchange, HttpStatus.BAD_REQUEST, "The platform console is not a tenant to act on");
        }
        return tenantDirectory.resolveKey(targetKey)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(found -> {
                    if (found.isEmpty()) {
                        return reject(exchange, HttpStatus.NOT_FOUND, "No tenant '" + targetKey + "'");
                    }
                    TenantDescriptor target = found.get();
                    if (target.isMaintenance() && !READS.contains(exchange.getRequest().getMethod())) {
                        exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, "5");
                        return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "This workspace is being maintained. Changes are paused for a moment; please try again.");
                    }
                    if (!target.isActive() && !target.isMaintenance()) {
                        return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "This workspace is " + target.getStatus().toLowerCase());
                    }
                    String requiredModule = requiredModuleFor(path);
                    if (requiredModule != null && !target.hasModule(requiredModule)) {
                        return reject(exchange, HttpStatus.NOT_FOUND, "Not found");
                    }
                    exchange.getAttributes().put(TENANT_ATTRIBUTE, host);
                    exchange.getAttributes().put(ACTING_ATTRIBUTE, target);
                    return chain.filter(exchange.mutate()
                            .request(r -> r.headers(headers -> {
                                headers.remove(ACTING_HEADER);
                                headers.remove(TENANT_HEADER);
                                headers.set(TENANT_HEADER, host.getTenantKey());
                            }))
                            .build());
                });
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
