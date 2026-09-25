package com.civileng.marketplace.gateway.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A tenant being moved or restored: reads go on, writes are told to retry shortly. */
class MaintenanceGatewayTest {

    private final TenantDirectory directory = mock(TenantDirectory.class);
    private final TenantResolutionGlobalFilter filter = new TenantResolutionGlobalFilter(directory);

    private MockServerWebExchange send(MockServerHttpRequest request, AtomicBoolean passed) {
        TenantDescriptor acme = new TenantDescriptor();
        acme.setTenantKey("acme");
        acme.setStatus("MAINTENANCE");
        acme.setModules(Set.of("bookings"));
        when(directory.resolve(anyString())).thenReturn(Mono.just(acme));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = e -> {
            passed.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void readsPassAndWritesAreRefusedWithRetryAfter() {
        AtomicBoolean read = new AtomicBoolean();
        send(MockServerHttpRequest.get("http://acme.localhost/api/v1/bookings").header("Host", "acme.localhost").build(), read);
        assertThat(read).isTrue();

        AtomicBoolean write = new AtomicBoolean();
        MockServerWebExchange refused = send(MockServerHttpRequest.post("http://acme.localhost/api/v1/bookings")
                .header("Host", "acme.localhost").build(), write);
        assertThat(write).isFalse();
        assertThat(refused.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(refused.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }
}
