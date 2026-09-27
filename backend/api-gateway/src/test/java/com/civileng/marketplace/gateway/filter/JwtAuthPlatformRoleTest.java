package com.civileng.marketplace.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** The gateway's half of keeping platform staff and a tenant's own staff apart. */
class JwtAuthPlatformRoleTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("gateway-test-secret-key-that-is-long-enough!".getBytes());
    private final JwtAuthGatewayFilterFactory factory = new JwtAuthGatewayFilterFactory(SECRET);

    private static String token(String tenant, String role) {
        return Jwts.builder().subject("1").claim("tenant", tenant).claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();
    }

    private HttpStatus run(String tenant, String role, String path, AtomicBoolean reached) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header("Authorization", "Bearer " + token(tenant, role)).header("X-Tenant-Id", tenant).build());
        factory.apply(new JwtAuthGatewayFilterFactory.Config())
                .filter(exchange, ex -> { reached.set(true); return Mono.empty(); }).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void platformStaffReachAdminPathsOnTheOperatorTenant() {
        for (String role : new String[]{"PLATFORM_OWNER", "PLATFORM_ADMIN", "PLATFORM_SUPPORT"}) {
            AtomicBoolean reached = new AtomicBoolean();
            run("platform", role, "/api/v1/admin/users", reached);
            assertThat(reached).as(role).isTrue();
        }
    }

    @Test
    void aTenantOwnerReachesAdminPathsOnTheirTenant() {
        AtomicBoolean reached = new AtomicBoolean();
        run("civengmarket", "TENANT_OWNER", "/api/v1/admin/users", reached);
        assertThat(reached).isTrue();
    }

    @Test
    void aPlatformRoleInACustomerTenantsTokenIsRefused() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThat(run("civengmarket", "PLATFORM_OWNER", "/api/v1/users/me", reached))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reached).isFalse();
    }

    @Test
    void theRetiredSuperAdminRoleOpensNoAdminPath() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThat(run("civengmarket", "SUPER_ADMIN", "/api/v1/admin/users", reached))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reached).isFalse();
    }
}
