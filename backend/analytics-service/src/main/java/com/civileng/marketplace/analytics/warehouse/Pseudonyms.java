package com.civileng.marketplace.analytics.warehouse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Keyed pseudonyms for people in the warehouse (architecture 02 §8: "PII columns are tokenised"):
 * HMAC-SHA256 of tenant and id, truncated. Stable, so distinct customers can still be counted;
 * not reversible without the key, and different for the same id in another tenant.
 */
public class Pseudonyms {

    private final SecretKeySpec key;

    public Pseudonyms(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("analytics.pseudonym-key is not set: people cannot be pseudonymised");
        }
        this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key.trim()), "HmacSHA256");
    }

    public String of(String tenantKey, Object id) {
        if (id == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] h = mac.doFinal((tenantKey + "|" + id).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
