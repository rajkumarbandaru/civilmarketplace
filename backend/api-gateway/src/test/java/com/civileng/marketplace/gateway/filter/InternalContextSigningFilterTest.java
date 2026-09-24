package com.civileng.marketplace.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalContextSigningFilterTest {

    private static final byte[] KEY = InternalContextSignature.decodeKey("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
    /** The same vector tenant-common's InternalContextSignatureTest asserts; see its javadoc. */
    private static final String VECTOR = "v1:1790000000:LQhELGFA-VPI8BUSiFqJ53O_MPeTcxVc4ohp46ODSBA";

    private final InternalContextSigningFilter filter = new InternalContextSigningFilter(KEY,
            Clock.fixed(Instant.ofEpochSecond(1_790_000_000L), ZoneOffset.UTC));

    private HttpHeaders forward(MockServerHttpRequest request) {
        AtomicReference<HttpHeaders> seen = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(request), ex -> {
            seen.set(ex.getRequest().getHeaders());
            return Mono.empty();
        }).block();
        return seen.get();
    }

    @Test
    void signsWhatTheServicesWillCheckWithTheSharedFormat() {
        HttpHeaders out = forward(MockServerHttpRequest.get("/api/v1/users/me")
                .header("X-Tenant-Id", "acme").header("X-User-Id", "7").header("X-User-Role", "CUSTOMER")
                .header("X-User-Email", "asha@example.com").header("X-User-Name", "Asha Rao").build());
        assertThat(out.getFirst(InternalContextSignature.HEADER)).isEqualTo(VECTOR);
    }

    @Test
    void signsATenantOnlyRequest() {
        HttpHeaders out = forward(MockServerHttpRequest.get("/api/v1/catalogue/services").header("X-Tenant-Id", "acme").build());
        assertThat(out.getFirst(InternalContextSignature.HEADER)).startsWith("v1:1790000000:");
    }

    @Test
    void leavesRequestsWithoutIdentityAlone() {
        HttpHeaders out = forward(MockServerHttpRequest.post("/webhooks/payments/razorpay/tok").build());
        assertThat(out.containsKey(InternalContextSignature.HEADER)).isFalse();
    }

    @Test
    void runsAfterRouteFiltersButBeforeProxying() {
        assertThat(filter.getOrder()).isGreaterThan(new IdentityHeaderStripFilter().getOrder());
        assertThat(filter.getOrder()).isLessThan(new NettyRoutingFilter(null, null, null).getOrder());
    }
}
