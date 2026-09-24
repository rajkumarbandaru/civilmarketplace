package com.civileng.marketplace.tenant.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalContextFilterTest {

    private final InternalContextFilter filter = new InternalContextFilter(InternalContextSignatureTest.KEY, 120,
            Clock.fixed(Instant.ofEpochSecond(InternalContextSignatureTest.TS + 5), ZoneOffset.UTC));

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest withIdentity() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/v1/users/me");
        InternalContextSignatureTest.identity().forEach(r::addHeader);
        return r;
    }

    @Test
    void passesSignedIdentity() throws Exception {
        MockHttpServletRequest r = withIdentity();
        r.addHeader(InternalContextSignature.HEADER, InternalContextSignatureTest.VECTOR);
        MockFilterChain chain = new MockFilterChain();
        assertThat(run(r, chain).getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void refusesUnsignedOrForgedIdentity() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse unsigned = run(withIdentity(), chain);
        assertThat(unsigned.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest forged = new MockHttpServletRequest("GET", "/api/v1/users/admin/kyc/pending");
        forged.addHeader("X-Tenant-Id", "acme");
        forged.addHeader("X-User-Role", "SUPER_ADMIN");
        forged.addHeader(InternalContextSignature.HEADER, InternalContextSignatureTest.VECTOR);
        assertThat(run(forged, new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    @Test
    void aRequestWithNoIdentityIsNotThisFiltersBusiness() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        assertThat(run(new MockHttpServletRequest("GET", "/actuator/health"), chain).getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void runsBeforeTheTenantFilter() {
        assertThat(filter.getOrder()).isLessThan(new TenantHeaderFilter(new TenantProperties()).getOrder());
    }

    @SuppressWarnings("unused")
    private static Map<String, String> unused() { return Map.of(); }
}
