package com.civileng.marketplace.tenant.common.integration;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for tenant integration secrets.
 *
 * <p>The tenant key and capability are bound in as additional authenticated data, so a ciphertext
 * copied from tenant A's row into tenant B's — or from A's SMS row into A's payment row — fails to
 * decrypt instead of quietly handing B the credentials of A. The row's own columns cannot vouch for
 * which tenant a secret belongs to; the AAD can.
 *
 * <p>Stored as {@code v1.<iv>.<ciphertext+tag>}, base64. The version prefix leaves room for a
 * key rotation that re-encrypts rows lazily.
 */
public final class IntegrationCipher {

    private static final String PREFIX = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key a 32-byte key, base64-encoded ({@code openssl rand -base64 32})
     */
    public IntegrationCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "platform.integrations.master-key is not set — tenant integration secrets "
                            + "cannot be encrypted or read without it");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("platform.integrations.master-key is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "platform.integrations.master-key must decode to 32 bytes, not " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext, String tenantKey, IntegrationCapability capability) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(tenantKey, capability));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder b64 = Base64.getEncoder();
            return PREFIX + "." + b64.encodeToString(iv) + "." + b64.encodeToString(sealed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt integration secret", e);
        }
    }

    public String decrypt(String stored, String tenantKey, IntegrationCapability capability) {
        String[] parts = stored == null ? new String[0] : stored.split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new IllegalStateException("Unrecognised integration secret format");
        }
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, b64.decode(parts[1])));
            cipher.updateAAD(aad(tenantKey, capability));
            return new String(cipher.doFinal(b64.decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Deliberately vague: which of key, tenant or tampering failed is not for a log line.
            throw new IllegalStateException(
                    "Integration secret for " + tenantKey + "/" + capability.key()
                            + " could not be decrypted", e);
        }
    }

    private static byte[] aad(String tenantKey, IntegrationCapability capability) {
        return (tenantKey + "|" + capability.name()).getBytes(StandardCharsets.UTF_8);
    }
}
