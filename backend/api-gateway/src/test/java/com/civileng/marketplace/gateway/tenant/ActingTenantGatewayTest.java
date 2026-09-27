package com.civileng.marketplace.gateway.tenant;

import com.civileng.marketplace.gateway.filter.ActingTenantGuardFilter;
import com.civileng.marketplace.gateway.filter.JwtAuthGatewayFilterFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Platform staff acting on a customer tenant from the platform console: tenant resolution vets the
 * target, the JWT filter vets the caller and switches the tenant, the guard refuses anything that
 * skipped the JWT filter. Run here as the three filters run in the gateway, in order.
 */
class ActingTenantGatewayTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("gateway-test-secret-key-that-is-long-enough!".getBytes());

    private final TenantDirectory directory = mock(TenantDirectory.class);
    private final TenantResolutionGlobalFilter resolution = new TenantResolutionGlobalFilter(directory);
    private final JwtAuthGatewayFilterFactory jwt = new JwtAuthGatewayFilterFactory(SECRET);
    private final ActingTenantGuardFilter guard = new ActingTenantGuardFilter();

    @BeforeEach
    void tenants() {
        when(directory.resolve("platform.localhost")).thenReturn(Mono.just(tenant("platform", "ACTIVE", "admin", "tenantadmin")));
        when(directory.resolve("civengmarket.localhost")).thenReturn(Mono.just(tenant("civengmarket", "ACTIVE", "admin", "bookings")));
        when(directory.resolveKey(anyString())).thenReturn(Mono.empty());
        when(directory.resolveKey("civengmarket")).thenReturn(Mono.just(tenant("civengmarket", "ACTIVE", "admin", "bookings")));
        when(directory.resolveKey("suspended")).thenReturn(Mono.just(tenant("suspended", "SUSPENDED", "admin")));
        when(directory.resolveKey("nobookings")).thenReturn(Mono.just(tenant("nobookings", "ACTIVE", "admin")));
    }

    private static TenantDescriptor tenant(String key, String status, String... modules) {
        TenantDescriptor t = new TenantDescriptor();
        t.setTenantKey(key);
        t.setStatus(status);
        t.setModules(Set.of(modules));
        return t;
    }

    private static String token(String tenant, String role) {
        return Jwts.builder().subject("1").claim("tenant", tenant).claim("role", role).claim("email", "staff@rk.test")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();
    }

    /** What reached the service (null if refused) and the exchange, for its status. */
    private record Result(HttpHeaders forwarded, MockServerWebExchange exchange) {
        HttpStatus status() {
            return (HttpStatus) exchange.getResponse().getStatusCode();
        }
    }

    private Result send(HttpMethod method, String host, String path, String actingOn, String tokenTenant, String role,
                        boolean authenticatedRoute) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.method(method, "http://" + host + path)
                .header("Host", host);
        if (actingOn != null) request.header(TenantResolutionGlobalFilter.ACTING_HEADER, actingOn);
        if (role != null) request.header("Authorization", "Bearer " + token(tokenTenant, role));
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        GatewayFilterChain service = e -> {
            forwarded.set(e.getRequest().getHeaders());
            return Mono.empty();
        };
        GatewayFilterChain afterRoute = e -> guard.filter(e, service);
        GatewayFilterChain route = authenticatedRoute
                ? e -> jwt.apply(new JwtAuthGatewayFilterFactory.Config()).filter(e, afterRoute)
                : afterRoute;
        resolution.filter(exchange, route).block();
        return new Result(forwarded.get(), exchange);
    }

    private Result act(HttpMethod method, String path, String role) {
        return send(method, "platform.localhost", path, "civengmarket", "platform", role, true);
    }

    @Test
    void platformOwnerAndAdminWorkOnTheTenantsAdminApis() {
        for (String role : new String[]{"PLATFORM_OWNER", "PLATFORM_ADMIN"}) {
            Result r = act(HttpMethod.PUT, "/api/v1/admin/services/7", role);
            assertThat(r.forwarded()).as(role).isNotNull();
            assertThat(r.forwarded().getFirst("X-Tenant-Id")).isEqualTo("civengmarket");
            assertThat(r.forwarded().getFirst("X-User-Acting-From")).isEqualTo("platform");
            assertThat(r.forwarded().getFirst("X-User-Role")).isEqualTo(role);
            assertThat(r.forwarded().containsKey(TenantResolutionGlobalFilter.ACTING_HEADER))
                    .as("the request header itself is never forwarded").isFalse();
        }
    }

    @Test
    void theTargetsModulesApplyNotThePlatforms() {
        // The platform has no bookings module; the tenant does.
        assertThat(act(HttpMethod.GET, "/api/v1/bookings/b1/tracking", "PLATFORM_ADMIN").forwarded()).isNotNull();

        Result r = send(HttpMethod.GET, "platform.localhost", "/api/v1/bookings/b1/tracking", "nobookings",
                "platform", "PLATFORM_ADMIN", true);
        assertThat(r.forwarded()).isNull();
        assertThat(r.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void platformSupportOnlyReads() {
        assertThat(act(HttpMethod.GET, "/api/v1/admin/bookings", "PLATFORM_SUPPORT").forwarded()).isNotNull();

        Result write = act(HttpMethod.POST, "/api/v1/admin/bookings/b1/cancel", "PLATFORM_SUPPORT");
        assertThat(write.forwarded()).isNull();
        assertThat(write.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberFacingApisAreNotReachableWhileActing() {
        for (String path : new String[]{"/api/v1/users/me", "/api/v1/notifications", "/api/v1/payments/create-order"}) {
            Result r = act(HttpMethod.GET, path, "PLATFORM_OWNER");
            assertThat(r.forwarded()).as(path).isNull();
            assertThat(r.status()).as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void tenantStaffCannotAct() {
        Result r = send(HttpMethod.GET, "civengmarket.localhost", "/api/v1/admin/users", "other",
                "civengmarket", "TENANT_OWNER", true);
        assertThat(r.forwarded()).isNull();
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aNonPlatformRoleOnThePlatformHostCannotAct() {
        Result r = send(HttpMethod.GET, "platform.localhost", "/api/v1/admin/users", "civengmarket",
                "platform", "CUSTOMER", true);
        assertThat(r.forwarded()).isNull();
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownOrSuspendedTargetIsRefused() {
        Result unknown = send(HttpMethod.GET, "platform.localhost", "/api/v1/admin/users", "ghost",
                "platform", "PLATFORM_OWNER", true);
        assertThat(unknown.forwarded()).isNull();
        assertThat(unknown.status()).isEqualTo(HttpStatus.NOT_FOUND);

        Result suspended = send(HttpMethod.GET, "platform.localhost", "/api/v1/admin/users", "suspended",
                "platform", "PLATFORM_OWNER", true);
        assertThat(suspended.forwarded()).isNull();
        assertThat(suspended.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void actingOnThePlatformItselfIsRefused() {
        Result r = send(HttpMethod.GET, "platform.localhost", "/api/v1/admin/users", "platform",
                "platform", "PLATFORM_OWNER", true);
        assertThat(r.forwarded()).isNull();
        assertThat(r.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anActingRequestOnAPublicRouteIsRefused() {
        Result r = send(HttpMethod.GET, "platform.localhost", "/api/v1/catalogue/services", "civengmarket",
                null, null, false);
        assertThat(r.forwarded()).isNull();
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void withoutTheHeaderNothingChanges() {
        Result r = send(HttpMethod.GET, "platform.localhost", "/api/v1/admin/users", null,
                "platform", "PLATFORM_OWNER", true);
        assertThat(r.forwarded().getFirst("X-Tenant-Id")).isEqualTo("platform");
        assertThat(r.forwarded().containsKey("X-User-Acting-From")).isFalse();
    }

    @Test
    void theTokenMustStillBeTheOperatorsOwn() {
        // A civengmarket token sent to the platform host: refused as before, acting or not.
        Result r = send(HttpMethod.GET, "platform.localhost", "/api/v1/admin/users", "civengmarket",
                "civengmarket", "TENANT_OWNER", true);
        assertThat(r.forwarded()).isNull();
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
