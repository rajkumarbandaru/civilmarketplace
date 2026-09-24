package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Server-side state for every refresh token issued.
 *
 * <p>Each token has one Redis key, {@code refresh_token:{tenant}:{userId}:{token}}, whose value is either
 * {@code valid} or {@code rotated:<epochMillis>}. The rotated marker is kept for the rest of the
 * token's life rather than deleted, because it is what tells a <em>stolen</em> token being replayed
 * apart from one that was simply revoked or never existed:
 *
 * <ul>
 *   <li>{@code valid} → exchange it: mark it rotated and issue a successor.</li>
 *   <li>{@code rotated}, within the grace window → a benign race (two tabs restoring from the same
 *       remembered token, a retried request whose response was lost). Issue a successor without
 *       alarm.</li>
 *   <li>{@code rotated}, after the grace window → reuse. Someone else already exchanged this token,
 *       so either the legitimate client or an attacker holds a copy it should not. Every session of
 *       the user is revoked (OAuth 2.0 Security BCP, refresh token rotation with reuse detection).</li>
 *   <li>missing → signed out, revoked, or expired. Refused, with no further action.</li>
 * </ul>
 *
 * <p>The tenant is part of the key because user ids are per-tenant sequences: without it, signing
 * user 7 out everywhere in one workspace also signed out user 7 of every other workspace.
 * Tokens issued before the tenant was added live under {@code refresh_token:{userId}:{token}}; a
 * refresh falls back to that key so nobody is signed out by the change. They expire within 30
 * days of it, after which {@link #LEGACY_PREFIX} and its uses can be deleted.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";
    /** Pre-tenant keys. Remove 30 days after deploying tenant-scoped keys. */
    private static final String LEGACY_PREFIX = "refresh_token:";
    private static final String VALID = "valid";
    private static final String ROTATED_PREFIX = "rotated:";
    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 30;

    /**
     * Swaps {@code valid} for the rotated marker atomically and returns what was there before.
     * Two concurrent exchanges of one token therefore cannot both see {@code valid}. KEEPTTL keeps
     * the marker alive exactly as long as the token itself could be presented.
     */
    private static final RedisScript<Object> ROTATE = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if v == ARGV[1] then redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL') end "
                    + "return v",
            Object.class);

    public enum Outcome { ROTATED, GRACE, REUSED, UNKNOWN }

    private final RedisTemplate<String, Object> redisTemplate;
    private final long reuseGraceMillis;

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate,
                               @Value("${jwt.refresh-reuse-grace-ms:30000}") long reuseGraceMillis) {
        this.redisTemplate = redisTemplate;
        this.reuseGraceMillis = reuseGraceMillis;
    }

    public void storeRefreshToken(String userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId, refreshToken), VALID, REFRESH_TOKEN_EXPIRATION_DAYS, TimeUnit.DAYS);
    }

    /** Marks the token spent and says how the caller should treat the exchange. */
    public Outcome rotate(String userId, String refreshToken) {
        Outcome outcome = rotateKey(key(userId, refreshToken));
        return outcome != Outcome.UNKNOWN ? outcome : rotateKey(legacyKey(userId, refreshToken));
    }

    private Outcome rotateKey(String key) {
        Object previous = redisTemplate.execute(
                ROTATE, List.of(key), VALID, ROTATED_PREFIX + System.currentTimeMillis());
        return classify(previous, System.currentTimeMillis());
    }

    /** True only for a live, never-exchanged token. */
    public boolean isValidRefreshToken(String userId, String refreshToken) {
        return VALID.equals(redisTemplate.opsForValue().get(key(userId, refreshToken)))
                || VALID.equals(redisTemplate.opsForValue().get(legacyKey(userId, refreshToken)));
    }

    public void revokeRefreshToken(String userId, String refreshToken) {
        redisTemplate.delete(List.of(key(userId, refreshToken), legacyKey(userId, refreshToken)));
    }

    /**
     * Signs the user out everywhere. SCAN rather than KEYS: KEYS walks the whole keyspace in one
     * blocking call, which stalls every other Redis client while it runs.
     */
    public void revokeAllUserTokens(String userId) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + TenantContext.require() + ":" + userId + ":*").count(500).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Revoked {} refresh token(s) for user {}", keys.size(), userId);
    }

    Outcome classify(Object previous, long now) {
        if (previous == null) {
            return Outcome.UNKNOWN;
        }
        String value = previous.toString();
        if (VALID.equals(value)) {
            return Outcome.ROTATED;
        }
        if (value.startsWith(ROTATED_PREFIX)) {
            long rotatedAt;
            try {
                rotatedAt = Long.parseLong(value.substring(ROTATED_PREFIX.length()));
            } catch (NumberFormatException e) {
                return Outcome.REUSED;
            }
            return now - rotatedAt <= reuseGraceMillis ? Outcome.GRACE : Outcome.REUSED;
        }
        return Outcome.UNKNOWN;
    }

    private static String key(String userId, String refreshToken) {
        return KEY_PREFIX + TenantContext.require() + ":" + userId + ":" + refreshToken;
    }

    /**
     * Not swept by {@link #revokeAllUserTokens}: its pattern would match every tenant's user of
     * that id. A legacy session of a suspended user is still refused, because a refresh re-checks
     * the account's status.
     */
    private static String legacyKey(String userId, String refreshToken) {
        return LEGACY_PREFIX + userId + ":" + refreshToken;
    }
}
