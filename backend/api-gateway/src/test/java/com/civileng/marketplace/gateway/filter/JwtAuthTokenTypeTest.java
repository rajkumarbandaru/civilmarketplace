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

class JwtAuthTokenTypeTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("gateway-test-secret-key-that-is-long-enough!".getBytes());
    private final JwtAuthGatewayFilterFactory factory = new JwtAuthGatewayFilterFactory(SECRET);

    private static String token(String type) {
        var builder = Jwts.builder().subject("1").claim("tenant", "platform").claim("role", "CUSTOMER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)));
        if (type != null) builder.claim("type", type);
        return builder.compact();
    }

    private HttpStatus run(String token, AtomicBoolean reached) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token).header("X-Tenant-Id", "platform").build());
        factory.apply(new JwtAuthGatewayFilterFactory.Config())
                .filter(exchange, ex -> { reached.set(true); return Mono.empty(); }).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void anAccessTokenIsAccepted() {
        AtomicBoolean reached = new AtomicBoolean();
        run(token(null), reached);
        assertThat(reached).isTrue();
    }

    @Test
    void refreshTokensAndMfaTicketsAreNotAccessTokens() {
        for (String type : new String[]{"refresh", "mfa"}) {
            AtomicBoolean reached = new AtomicBoolean();
            assertThat(run(token(type), reached)).as(type).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(reached).as(type).isFalse();
        }
    }
}
