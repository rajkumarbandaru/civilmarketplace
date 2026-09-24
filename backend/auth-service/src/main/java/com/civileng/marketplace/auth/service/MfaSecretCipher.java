package com.civileng.marketplace.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts TOTP secrets at rest (AES-256-GCM). A database dump alone must not be enough to mint
 * a Super Admin's codes.
 *
 * <p>The key is derived from the JWT signing secret with a fixed label (HMAC-SHA256 as a KDF), so
 * it is a separate key for a separate purpose without another secret to provision. The user id and
 * tenant are bound as associated data: a ciphertext copied onto another row does not decrypt.
 */
@Component
public class MfaSecretCipher {

    private static final String LABEL = "civeng/auth/mfa-secret/v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public MfaSecretCipher(@Value("${jwt.secret}") String jwtSecret) {
        try {
            Mac kdf = Mac.getInstance("HmacSHA256");
            kdf.init(new SecretKeySpec(Base64.getDecoder().decode(jwtSecret), "HmacSHA256"));
            this.key = new SecretKeySpec(kdf.doFinal(LABEL.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive the MFA encryption key", e);
        }
    }

    public String encrypt(String plaintext, String tenant, Long userId) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenant, userId));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt MFA secret", e);
        }
    }

    public String decrypt(String stored, String tenant, Long userId) {
        try {
            if (!stored.startsWith("v1:")) throw new IllegalStateException("Unknown MFA secret format");
            byte[] all = Base64.getDecoder().decode(stored.substring(3));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            cipher.updateAAD(aad(tenant, userId));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt MFA secret", e);
        }
    }

    private static byte[] aad(String tenant, Long userId) {
        return (tenant + "/" + userId).getBytes(StandardCharsets.UTF_8);
    }
}
