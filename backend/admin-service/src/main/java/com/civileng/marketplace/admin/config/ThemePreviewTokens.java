package com.civileng.marketplace.admin.config;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand;
import com.civileng.marketplace.tenant.common.InternalContextAutoConfiguration.InternalSigningKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Preview tokens (architecture 05 §3.3): a short-lived, signed ticket carrying an unpublished
 * theme, so the real app can be opened with it before anyone publishes. Self-contained — the
 * candidate rides in the token, signed — so nothing is stored and any instance can read it.
 *
 * <p>Bound to the tenant it was issued in and to at most 15 minutes. It opens only the public
 * preview endpoint; no transactional API reads it.
 */
@Component
public class ThemePreviewTokens {

    static final Duration TTL = Duration.ofMinutes(15);
    private static final String LABEL = "civeng/admin/theme-preview/v1";

    private final byte[] key;
    private final ObjectMapper json;
    private final Clock clock;

    public record Payload(String tenant, Long admin, long exp, ThemeUpdateCommand values) { }

    public ThemePreviewTokens(InternalSigningKey signingKey, ObjectMapper json, Clock clock) {
        this.key = signingKey.bytes() == null ? null : derive(signingKey.bytes());
        this.json = json;
        this.clock = clock;
    }

    public String issue(String tenant, Long admin, ThemeUpdateCommand values) {
        if (key == null) throw new IllegalStateException("Previews need INTERNAL_SIGNING_KEY");
        try {
            String body = b64(json.writeValueAsBytes(new Payload(tenant, admin,
                    clock.instant().plus(TTL).getEpochSecond(), values)));
            return body + "." + b64(mac(body));
        } catch (Exception e) {
            throw new IllegalStateException("Could not issue a preview token", e);
        }
    }

    /** The candidate theme, if the token is genuine, unexpired and for this tenant. */
    public ThemeUpdateCommand read(String token, String tenant) {
        if (key == null || token == null) throw invalid();
        int dot = token.lastIndexOf('.');
        if (dot <= 0) throw invalid();
        String body = token.substring(0, dot);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(mac(body), given)) throw invalid();
        try {
            Payload p = json.readValue(Base64.getUrlDecoder().decode(body), Payload.class);
            if (!tenant.equals(p.tenant()) || clock.instant().getEpochSecond() > p.exp()) throw invalid();
            return p.values();
        } catch (java.util.NoSuchElementException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private java.util.NoSuchElementException invalid() {
        return new java.util.NoSuchElementException("This preview link has expired. Open a new preview from the theme editor.");
    }

    private byte[] mac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] derive(byte[] master) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(LABEL.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
