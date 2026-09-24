package com.civileng.marketplace.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityHeaderStripFilterTest {

    private final IdentityHeaderStripFilter filter = new IdentityHeaderStripFilter();

    private HttpHeaders forward(MockServerHttpRequest request) {
        AtomicReference<HttpHeaders> seen = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(request), ex -> {
            seen.set(ex.getRequest().getHeaders());
            return Mono.empty();
        }).block();
        return seen.get();
    }

    @Test
    void dropsForgedIdentityHeadersInAnyCase() {
        HttpHeaders out = forward(MockServerHttpRequest.get("/api/v1/catalogue/services")
                .header("X-User-Id", "1")
                .header("x-user-role", "SUPER_ADMIN")
                .header("X-USER-EMAIL", "a@b.c")
                .header("X-User-Something-New", "x")
                .header("X-Internal-Caller", "payment-service")
                .header("Accept", "application/json")
                .build());

        assertThat(out.keySet()).noneMatch(IdentityHeaderStripFilter::isIdentity);
        assertThat(out.getFirst("Accept")).isEqualTo("application/json");
    }

    @Test
    void leavesOrdinaryRequestsUntouched() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/geo/countries")
                .header("Authorization", "Bearer t").header("X-Request-Id", "r1").build();
        HttpHeaders out = forward(request);
        assertThat(out.getFirst("Authorization")).isEqualTo("Bearer t");
        assertThat(out.getFirst("X-Request-Id")).isEqualTo("r1");
    }

    @Test
    void runsBeforeTenantResolutionAndTheJwtFilter() {
        assertThat(filter.getOrder()).isLessThan(new com.civileng.marketplace.gateway.tenant
                .TenantResolutionGlobalFilter(null).getOrder());
    }
}
