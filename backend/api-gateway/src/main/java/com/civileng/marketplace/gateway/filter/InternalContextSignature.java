package com.civileng.marketplace.gateway.filter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * The gateway's signature over the identity it forwards, so a service can tell headers the gateway
 * (or another service) set from headers anyone who can reach its port typed.
 *
 * <p>Signed: the tenant, the user fields and a timestamp. Each value is length-prefixed so no
 * value can end early and shift the rest (a name containing a newline cannot become a role). The
 * timestamp bounds replay to {@code maxSkewSeconds}.
 *
 * <p>A copy of tenant-common's InternalContextSignature (the gateway does not depend on it); both
 * test the same fixed vector, so changing one without the other fails a build.
 */
public final class InternalContextSignature {

    public static final String HEADER = "X-Internal-Signature";

    /** Every header a service takes identity from. Order is part of the format. */
    public static final List<String> SIGNED_HEADERS = List.of(
            "X-Tenant-Id", "X-User-Id", "X-User-Role", "X-User-Email", "X-User-Name");

    private static final String VERSION = "v1";

    private InternalContextSignature() {
    }

    /** {@code v1:<epochSeconds>:<base64url HMAC-SHA256>} over the headers {@code header} returns. */
    public static String sign(byte[] key, Function<String, String> header, long epochSeconds) {
        return VERSION + ":" + epochSeconds + ":" + mac(key, canonical(header, epochSeconds));
    }

    public static boolean verify(byte[] key, Function<String, String> header, String signature,
                                 long nowEpochSeconds, long maxSkewSeconds) {
        if (signature == null) return false;
        String[] parts = signature.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) return false;
        long ts;
        try {
            ts = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > maxSkewSeconds) return false;
        String expected = mac(key, canonical(header, ts));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII));
    }

    /** True if the request carries any identity at all — and so needs a signature. */
    public static boolean carriesIdentity(Function<String, String> header) {
        return SIGNED_HEADERS.stream().anyMatch(h -> header.apply(h) != null);
    }

    static String canonical(Function<String, String> header, long epochSeconds) {
        StringBuilder sb = new StringBuilder(VERSION).append('\n').append(epochSeconds);
        for (String name : SIGNED_HEADERS) {
            String value = header.apply(name);
            if (value == null) {
                sb.append("\n-");
            } else {
                sb.append('\n').append(value.length()).append(':').append(value);
            }
        }
        return sb.toString();
    }

    private static String mac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute internal signature", e);
        }
    }

    /** Decodes the configured key; at least 32 bytes, as for any HMAC-SHA256 key. */
    public static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("INTERNAL_SIGNING_KEY is not set. Every service and the gateway "
                    + "need the same base64 key (openssl rand -base64 32), or set "
                    + "platform.internal.signature.enabled=false for a service run outside the stack.");
        }
        byte[] key = Base64.getDecoder().decode(base64.trim());
        if (key.length < 32) {
            throw new IllegalStateException("INTERNAL_SIGNING_KEY must decode to at least 32 bytes");
        }
        return key;
    }
}
