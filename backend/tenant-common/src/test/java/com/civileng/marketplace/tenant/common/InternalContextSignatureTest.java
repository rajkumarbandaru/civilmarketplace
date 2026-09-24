package com.civileng.marketplace.tenant.common;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalContextSignatureTest {

    static final byte[] KEY = InternalContextSignature.decodeKey("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
    static final long TS = 1_790_000_000L;
    /**
     * Computed independently (Python hmac) from the documented format. api-gateway's copy asserts
     * the same value: if either implementation changes, one of the two builds fails.
     */
    static final String VECTOR = "v1:1790000000:LQhELGFA-VPI8BUSiFqJ53O_MPeTcxVc4ohp46ODSBA";

    static Map<String, String> identity() {
        Map<String, String> h = new HashMap<>();
        h.put("X-Tenant-Id", "acme");
        h.put("X-User-Id", "7");
        h.put("X-User-Role", "CUSTOMER");
        h.put("X-User-Email", "asha@example.com");
        h.put("X-User-Name", "Asha Rao");
        return h;
    }

    @Test
    void matchesTheSharedVector() {
        assertThat(InternalContextSignature.sign(KEY, identity()::get, TS)).isEqualTo(VECTOR);
    }

    @Test
    void verifiesItsOwnSignatureWithinTheWindow() {
        assertThat(InternalContextSignature.verify(KEY, identity()::get, VECTOR, TS + 100, 120)).isTrue();
        assertThat(InternalContextSignature.verify(KEY, identity()::get, VECTOR, TS - 100, 120)).isTrue();
        assertThat(InternalContextSignature.verify(KEY, identity()::get, VECTOR, TS + 121, 120)).as("stale").isFalse();
    }

    @Test
    void anyChangedFieldBreaksIt() {
        for (String header : InternalContextSignature.SIGNED_HEADERS) {
            Map<String, String> h = identity();
            h.put(header, h.get(header) + "x");
            assertThat(InternalContextSignature.verify(KEY, h::get, VECTOR, TS, 120)).as(header).isFalse();
        }
        Map<String, String> escalated = identity();
        escalated.put("X-User-Role", "SUPER_ADMIN");
        assertThat(InternalContextSignature.verify(KEY, escalated::get, VECTOR, TS, 120)).isFalse();
        Map<String, String> dropped = identity();
        dropped.remove("X-User-Name");
        assertThat(InternalContextSignature.verify(KEY, dropped::get, VECTOR, TS, 120)).isFalse();
    }

    @Test
    void aValueCannotSpillIntoTheNextField() {
        Map<String, String> a = identity();
        a.put("X-User-Email", "x\n5:ADMIN");
        a.put("X-User-Name", null);
        Map<String, String> b = identity();
        b.put("X-User-Email", "x");
        b.put("X-User-Name", "ADMIN");
        assertThat(InternalContextSignature.canonical(a::get, TS)).isNotEqualTo(InternalContextSignature.canonical(b::get, TS));
    }

    @Test
    void wrongKeyMalformedOrMissingSignaturesFail() {
        byte[] other = InternalContextSignature.decodeKey("ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA=");
        assertThat(InternalContextSignature.verify(other, identity()::get, VECTOR, TS, 120)).isFalse();
        assertThat(InternalContextSignature.verify(KEY, identity()::get, null, TS, 120)).isFalse();
        assertThat(InternalContextSignature.verify(KEY, identity()::get, "v1:abc:x", TS, 120)).isFalse();
        assertThat(InternalContextSignature.verify(KEY, identity()::get, "v2:1790000000:x", TS, 120)).isFalse();
    }

    @Test
    void refusesAMissingOrShortKey() {
        assertThatThrownBy(() -> InternalContextSignature.decodeKey("")).hasMessageContaining("INTERNAL_SIGNING_KEY");
        assertThatThrownBy(() -> InternalContextSignature.decodeKey("c2hvcnQ=")).hasMessageContaining("32 bytes");
    }
}
