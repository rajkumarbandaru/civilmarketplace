package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.auth.service.RefreshTokenService.Outcome;
import com.civileng.marketplace.tenant.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RefreshTokenService - rotation state and reuse detection")
class RefreshTokenServiceTest {

    private static final long GRACE = 30_000;

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new RefreshTokenService(redis, GRACE);
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storesNewTokensAsValidForThirtyDays() {
        service.storeRefreshToken("7", "tok");
        verify(values).set("refresh_token:acme:7:tok", "valid", 30, TimeUnit.DAYS);
    }

    @Test
    void classifiesEveryState() {
        long now = 1_000_000;
        assertThat(service.classify("valid", now)).isEqualTo(Outcome.ROTATED);
        assertThat(service.classify("rotated:" + (now - 5_000), now)).isEqualTo(Outcome.GRACE);
        assertThat(service.classify("rotated:" + (now - GRACE - 1), now)).isEqualTo(Outcome.REUSED);
        assertThat(service.classify("rotated:garbage", now)).isEqualTo(Outcome.REUSED);
        assertThat(service.classify(null, now)).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotateSwapsAtomicallyThroughTheScript() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn("valid");
        assertThat(service.rotate("7", "tok")).isEqualTo(Outcome.ROTATED);
        verify(redis).execute(any(RedisScript.class), eq(List.of("refresh_token:acme:7:tok")),
                eq("valid"), argThat(v -> v.toString().startsWith("rotated:")));
    }

    @Test
    void onlyAnUnspentTokenIsValid() {
        when(values.get("refresh_token:acme:7:live")).thenReturn("valid");
        when(values.get("refresh_token:acme:7:spent")).thenReturn("rotated:1");
        assertThat(service.isValidRefreshToken("7", "live")).isTrue();
        assertThat(service.isValidRefreshToken("7", "spent")).isFalse();
        assertThat(service.isValidRefreshToken("7", "gone")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTokenIssuedBeforeTenantScopedKeysStillRotatesFromItsOldKey() {
        when(redis.execute(any(RedisScript.class), eq(List.of("refresh_token:acme:7:old")), any(), any())).thenReturn(null);
        when(redis.execute(any(RedisScript.class), eq(List.of("refresh_token:7:old")), any(), any())).thenReturn("valid");
        assertThat(service.rotate("7", "old")).isEqualTo(Outcome.ROTATED);

        when(values.get("refresh_token:7:legacy")).thenReturn("valid");
        assertThat(service.isValidRefreshToken("7", "legacy")).isTrue();
    }

    @Test
    void signOutEverywhereOnlyTouchesThisWorkspacesUser() {
        Cursor<String> cursor = mock(Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        service.revokeAllUserTokens("7");
        verify(redis).scan(argThat((ScanOptions o) -> "refresh_token:acme:7:*".equals(o.getPattern())));
    }

    @Test
    void keysNeedATenant() {
        TenantContext.clear();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.storeRefreshToken("7", "tok"))
                .isInstanceOf(IllegalStateException.class);
    }
}
