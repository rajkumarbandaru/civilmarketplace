package com.civileng.marketplace.gateway.filter;

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
    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

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

                // A token is only valid on the tenant it was issued for. Without this check a
                // SUPER_ADMIN of one workspace could point their token at another workspace's
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

                if (isAdminPath(path) && !ADMIN_ROLES.contains(role)) {
                    log.warn("Non-admin role '{}' refused on admin path {}", role, path);
                    return onError(exchange, "Admin role required", HttpStatus.FORBIDDEN);
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
