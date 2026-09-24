package com.civileng.marketplace.admin.config;

import com.civileng.marketplace.admin.uiconfig.dto.UiConfigDTO.ThemeUpdateCommand;
import com.civileng.marketplace.tenant.common.InternalContextAutoConfiguration.InternalSigningKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ThemePreviewTokensTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:00Z"));
    private final ThemePreviewTokens tokens;
    private static final ThemeUpdateCommand CANDIDATE = new ThemeUpdateCommand("dark", "#0057ff", "#00a3ff", null, null, 20,
            null, "Acme", null, "glass", "solid", "topbar", "comfortable", "marketplace");

    ThemePreviewTokensTest() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(inv -> now.get());
        tokens = new ThemePreviewTokens(new InternalSigningKey(new byte[32]), new ObjectMapper(), clock);
    }

    @Test
    void carriesTheCandidateForItsOwnTenant() {
        String token = tokens.issue("acme", 1L, CANDIDATE);
        assertThat(tokens.read(token, "acme")).isEqualTo(CANDIDATE);
    }

    @Test
    void isRefusedOnAnotherTenantAfterFifteenMinutesOrWhenAltered() {
        String token = tokens.issue("acme", 1L, CANDIDATE);
        assertThatThrownBy(() -> tokens.read(token, "bhoomi")).isInstanceOf(NoSuchElementException.class);
        String body = token.substring(0, token.lastIndexOf('.'));
        String sig = token.substring(token.lastIndexOf('.'));
        String forged = body.substring(0, body.length() - 2) + (body.endsWith("A") ? "B" : "A") + body.charAt(body.length() - 1) + sig;
        assertThatThrownBy(() -> tokens.read(forged, "acme")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> tokens.read("garbage", "acme")).isInstanceOf(NoSuchElementException.class);
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertThatThrownBy(() -> tokens.read(token, "acme")).hasMessageContaining("expired");
    }

    @Test
    void cannotBeIssuedWithoutASigningKey() {
        ThemePreviewTokens off = new ThemePreviewTokens(new InternalSigningKey(null), new ObjectMapper(), Clock.systemUTC());
        assertThatThrownBy(() -> off.issue("acme", 1L, CANDIDATE)).isInstanceOf(IllegalStateException.class);
    }
}
