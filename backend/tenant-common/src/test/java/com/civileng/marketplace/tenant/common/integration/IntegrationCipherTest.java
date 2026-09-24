package com.civileng.marketplace.tenant.common.integration;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final IntegrationCipher cipher = new IntegrationCipher(KEY);

    @Test
    void roundTripsForTheSameTenantAndCapability() {
        String sealed = cipher.encrypt("{\"keySecret\":\"s3cret\"}", "acme", IntegrationCapability.PAYMENT);

        assertThat(sealed).startsWith("v1.").doesNotContain("s3cret");
        assertThat(cipher.decrypt(sealed, "acme", IntegrationCapability.PAYMENT))
                .isEqualTo("{\"keySecret\":\"s3cret\"}");
    }

    @Test
    void ciphertextCopiedToAnotherTenantDoesNotDecrypt() {
        String sealed = cipher.encrypt("secret", "acme", IntegrationCapability.PAYMENT);

        assertThatThrownBy(() -> cipher.decrypt(sealed, "bhoomi", IntegrationCapability.PAYMENT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ciphertextCopiedToAnotherCapabilityDoesNotDecrypt() {
        String sealed = cipher.encrypt("secret", "acme", IntegrationCapability.SMS);

        assertThatThrownBy(() -> cipher.decrypt(sealed, "acme", IntegrationCapability.PAYMENT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String sealed = cipher.encrypt("secret", "acme", IntegrationCapability.EMAIL);
        char[] chars = sealed.toCharArray();
        int last = chars.length - 2;
        chars[last] = chars[last] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> cipher.decrypt(new String(chars), "acme", IntegrationCapability.EMAIL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usesAFreshIvPerEncryption() {
        assertThat(cipher.encrypt("same", "acme", IntegrationCapability.AI))
                .isNotEqualTo(cipher.encrypt("same", "acme", IntegrationCapability.AI));
    }

    @Test
    void rejectsMissingOrWrongSizedKeys() {
        assertThatThrownBy(() -> new IntegrationCipher(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IntegrationCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
