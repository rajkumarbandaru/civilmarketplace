package com.civileng.marketplace.gateway.filter;

import com.civileng.marketplace.gateway.tenant.TenantDescriptor;
import com.civileng.marketplace.gateway.tenant.TenantResolutionGlobalFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class JwtAuthGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

    /**
     * Every path under this prefix is a staff surface — verified across all eleven services: there
     * is no member-facing endpoint below it. Guarding it here rather than per route means a new
     * admin route added to {@link com.civileng.marketplace.gateway.config.GatewayConfig} is gated
     * the moment it exists, instead of relying on whoever adds it to remember.
     */
    private static final String ADMIN_PREFIX = "/api/v1/admin";

    /** The staff roles seeded by auth-service. Anything else is a member. */
    private static final Set<String> ADMIN_ROLES = Set.of(
            "TENANT_OWNER", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN",
            "PLATFORM_OWNER", "PLATFORM_ADMIN", "PLATFORM_SUPPORT");

    /** The platform company's own staff: only ever valid on the operator tenant. */
    private static final Set<String> PLATFORM_ROLES =
            Set.of("PLATFORM_OWNER", "PLATFORM_ADMIN", "PLATFORM_SUPPORT");

    private static final String OPERATOR_TENANT = TenantResolutionGlobalFilter.OPERATOR_TENANT;

    /** Signed downstream with the rest of the identity; names the tenant an acting caller lives in. */
    public static final String ACTING_FROM_HEADER = "X-User-Acting-From";

    /** Set once this filter has let an acting request through; ActingTenantGuardFilter checks it. */
    public static final String ACTING_APPLIED_ATTRIBUTE = "platform.actingApplied";

    /**
     * Where platform staff may go while acting on a tenant: the staff screens' APIs. Never the
     * member-facing ones — the caller's user id belongs to the operator tenant and names nobody (or
     * somebody else) in the tenant being acted on, so a "my profile" call there would be wrong.
     */
    private static final List<String> ACTING_PREFIXES = List.of(
            "/api/v1/admin", "/api/v1/users/admin", "/api/v1/workspace-settings");

    /** Read-only extras the staff screens load: the workspace's KPIs, a booking's live track, media. */
    private static final List<java.util.regex.Pattern> ACTING_READS = List.of(
            java.util.regex.Pattern.compile("^/api/v1/analytics/workspace$"),
            java.util.regex.Pattern.compile("^/api/v1/bookings/[^/]+/tracking$"),
            java.util.regex.Pattern.compile("^/api/v1/media/.+$"));

    private static final Set<HttpMethod> READS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final SecretKey secretKey;

    public JwtAuthGatewayFilterFactory(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest()
                    .getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid authorization header",
                        HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // Only access tokens open the API. Refresh tokens (30 days) and MFA tickets carry a
                // "type" claim; accepting them here would let a refresh token stand in for an
                // access token for its whole life, and an MFA ticket skip the second factor.
                if (claims.get("type") != null) {
                    log.warn("Refused a '{}' token used as an access token", claims.get("type"));
                    return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
                }

                // A token is only valid on the tenant it was issued for. Without this check a
                // workspace owner of one workspace could point their token at another workspace's
                // subdomain and be served as an admin there, since downstream services trust the
                // resolved X-Tenant-Id header unconditionally.
                String tokenTenant = claims.get("tenant", String.class);
                String resolvedTenant = exchange.getRequest()
                        .getHeaders().getFirst(TenantResolutionGlobalFilter.TENANT_HEADER);

                if (tokenTenant == null || !tokenTenant.equals(resolvedTenant)) {
                    log.warn("Token issued for tenant '{}' presented on tenant '{}'",
                            tokenTenant, resolvedTenant);
                    return onError(exchange, "Token is not valid for this workspace",
                            HttpStatus.FORBIDDEN);
                }

                // The role gate. Downstream services check this themselves too — this is the
                // outer of two layers, not the only one, because a service reachable on its own
                // port inside the network must not depend on the gateway for its authorisation.
                String role = claims.get("role", String.class);
                String path = exchange.getRequest().getPath().value();

                // A platform role in a token for any tenant but the operator is not a real
                // account — auth-service never assigns one there — so it is refused outright
                // rather than passed on for every service to second-guess.
                if (PLATFORM_ROLES.contains(role) && !OPERATOR_TENANT.equals(tokenTenant)) {
                    log.warn("Platform role '{}' in a token for tenant '{}'", role, tokenTenant);
                    return onError(exchange, "Token is not valid for this workspace",
                            HttpStatus.FORBIDDEN);
                }

                if (isAdminPath(path) && !ADMIN_ROLES.contains(role)) {
                    log.warn("Non-admin role '{}' refused on admin path {}", role, path);
                    return onError(exchange, "Admin role required", HttpStatus.FORBIDDEN);
                }

                TenantDescriptor acting = exchange.getAttribute(TenantResolutionGlobalFilter.ACTING_ATTRIBUTE);
                if (acting != null) {
                    HttpMethod method = exchange.getRequest().getMethod();
                    String refusal = actingRefusal(tokenTenant, role, path, method);
                    if (refusal != null) {
                        log.warn("Refused acting on '{}' for role '{}': {} {} ({})",
                                acting.getTenantKey(), role, method, path, refusal);
                        return onError(exchange, refusal, HttpStatus.FORBIDDEN);
                    }
                    log.info("Platform user {} ({}) acting on tenant '{}': {} {}",
                            claims.getSubject(), role, acting.getTenantKey(), method, path);
                    exchange.getAttributes().put(ACTING_APPLIED_ATTRIBUTE, Boolean.TRUE);
                    String actingFrom = tokenTenant;
                    exchange = exchange.mutate()
                            .request(r -> r.headers(h -> {
                                h.set(TenantResolutionGlobalFilter.TENANT_HEADER, acting.getTenantKey());
                                h.set(ACTING_FROM_HEADER, actingFrom);
                            }))
                            .build();
                }

                exchange = exchange.mutate()
                        .request(r -> r
                                .header("X-User-Id", claims.getSubject())
                                .header("X-User-Email",
                                        claims.get("email", String.class))
                                .header("X-User-Role",
                                        claims.get("role", String.class))
                                .header("X-User-Name",
                                        claims.get("name", String.class)))
                        .build();

                return chain.filter(exchange);

            } catch (ExpiredJwtException e) {
                log.warn("Expired JWT token: {}", e.getMessage());
                return onError(exchange, "Token has expired", HttpStatus.UNAUTHORIZED);
            } catch (JwtException e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    /**
     * Why a request to act on a tenant is refused, or null if it may go ahead: the caller must be
     * platform staff signed in on the operator tenant, on a staff-screen path, and platform support
     * may only read.
     */
    static String actingRefusal(String tokenTenant, String role, String path, HttpMethod method) {
        if (!OPERATOR_TENANT.equals(tokenTenant) || !PLATFORM_ROLES.contains(role)) {
            return "Only platform staff can act on a tenant";
        }
        boolean read = method != null && READS.contains(method);
        boolean staffPath = ACTING_PREFIXES.stream().anyMatch(prefix -> onSegment(path, prefix));
        boolean readPath = ACTING_READS.stream().anyMatch(pattern -> pattern.matcher(path).matches());
        if (!staffPath && !(read && readPath)) {
            return "This is not available while acting on a tenant";
        }
        if ("PLATFORM_SUPPORT".equals(role) && !read) {
            return "Platform support staff have read-only access";
        }
        return null;
    }

    private static boolean onSegment(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * Prefix match, but only on a segment boundary — a future `/api/v1/administrators` route must
     * not be swept into the admin gate by a bare {@code startsWith}.
     */
    private static boolean isAdminPath(String path) {
        return path.equals(ADMIN_PREFIX) || path.startsWith(ADMIN_PREFIX + "/");
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message,
                               HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = String.format(
                "{\"success\":false,\"message\":\"%s\",\"status\":%d}",
                message, status.value());
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    public static class Config {
        private List<String> excludedPaths = List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/otp",
                "/api/v1/auth/refresh",
                "/oauth2/**",
                "/webhooks/**"
        );

        public List<String> getExcludedPaths() {
            return excludedPaths;
        }

        public void setExcludedPaths(List<String> excludedPaths) {
            this.excludedPaths = excludedPaths;
        }
    }
}
