package com.civileng.marketplace.tenant.common;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantFeignInterceptorSigningTest {

    private static final long NOW = InternalContextSignatureTest.TS;
    private final TenantFeignInterceptor interceptor = new TenantFeignInterceptor(InternalContextSignatureTest.KEY,
            Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    @AfterEach
    void clear() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private static String first(RequestTemplate t, String name) {
        return t.headers().getOrDefault(name, java.util.List.of()).stream().findFirst().orElse(null);
    }

    private static boolean verifies(RequestTemplate t) {
        Map<String, String> h = new HashMap<>();
        InternalContextSignature.SIGNED_HEADERS.forEach(n -> h.put(n, first(t, n)));
        return InternalContextSignature.verify(InternalContextSignatureTest.KEY, h::get,
                first(t, InternalContextSignature.HEADER), NOW, 120);
    }

    @Test
    void forwardsAndSignsTheInboundCallersIdentity() {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        InternalContextSignatureTest.identity().forEach(inbound::addHeader);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
        TenantContext.set("acme");

        RequestTemplate t = new RequestTemplate();
        interceptor.apply(t);

        assertThat(first(t, "X-User-Role")).isEqualTo("CUSTOMER");
        assertThat(first(t, InternalContextSignature.HEADER)).isEqualTo(InternalContextSignatureTest.VECTOR);
        assertThat(verifies(t)).isTrue();
    }

    @Test
    void aBackgroundJobSignsJustItsTenant() {
        TenantContext.set("acme");
        RequestTemplate t = new RequestTemplate();
        interceptor.apply(t);
        assertThat(first(t, "X-Tenant-Id")).isEqualTo("acme");
        assertThat(verifies(t)).isTrue();
    }

    @Test
    void anExplicitHeaderIsKeptAndSigned() {
        TenantContext.set("acme");
        RequestTemplate t = new RequestTemplate().header("X-User-Role", "SUPER_ADMIN");
        interceptor.apply(t);
        assertThat(first(t, "X-User-Role")).isEqualTo("SUPER_ADMIN");
        assertThat(verifies(t)).isTrue();
    }

    @Test
    void theTenantIsReplacedNotAppended() {
        TenantContext.set("acme");
        RequestTemplate t = new RequestTemplate().header("X-Tenant-Id", "acme");
        interceptor.apply(t);
        assertThat(t.headers().get("X-Tenant-Id")).containsExactly("acme");
        interceptor.apply(t);
        assertThat(t.headers().get(InternalContextSignature.HEADER)).hasSize(1);
    }

    @Test
    void nothingToSignWithoutATenantOrUser() {
        RequestTemplate t = new RequestTemplate();
        interceptor.apply(t);
        assertThat(t.headers()).doesNotContainKey(InternalContextSignature.HEADER);
    }
}
