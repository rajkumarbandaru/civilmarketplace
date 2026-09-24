package com.civileng.marketplace.auth.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 Appendix B, SHA-1 column: the reference every authenticator app agrees with. */
    @Test
    void matchesTheRfc6238TestVectors() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        long[][] vectors = {{59, 94287082}, {1111111109, 7081804}, {1111111111, 14050471},
                {1234567890, 89005924}, {2000000000, 69279037}, {20000000000L, 65353130}};
        for (long[] v : vectors) {
            assertThat(Totp.code(key, Totp.stepAt(v[0]), 8, "HmacSHA1"))
                    .as("T=" + v[0]).isEqualTo(String.format("%08d", v[1]));
        }
    }

    @Test
    void base32RoundTripsAndSecretsAre160Bits() {
        String secret = Totp.newSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(Totp.base32Decode(secret)).hasSize(20);
        assertThat(Totp.base32(Totp.base32Decode(secret))).isEqualTo(secret);
        assertThat(Totp.base32Decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
                .isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void acceptsOneStepOfDriftAndNothingElse() {
        String secret = Totp.newSecret();
        long now = 1_790_000_000L;
        long step = Totp.stepAt(now);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step), now, null)).isEqualTo(step);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step - 1), now, null)).isEqualTo(step - 1);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step + 1), now, null)).isEqualTo(step + 1);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step - 2), now, null)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, "12345", now, null)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, null, now, null)).isEqualTo(-1);
    }

    @Test
    void aCodeAlreadyUsedIsRefused() {
        String secret = Totp.newSecret();
        long now = 1_790_000_000L;
        String code = Totp.code(secret, Totp.stepAt(now));
        assertThat(Totp.matchingStep(secret, code, now, Totp.stepAt(now))).isEqualTo(-1);
    }

    @Test
    void buildsTheUriAppsScan() {
        assertThat(Totp.otpauthUri("Civil Marketplace (acme)", "ops@acme.in", "ABC"))
                .isEqualTo("otpauth://totp/Civil%20Marketplace%20%28acme%29:ops%40acme.in?secret=ABC"
                        + "&issuer=Civil%20Marketplace%20%28acme%29&algorithm=SHA1&digits=6&period=30");
    }
}
