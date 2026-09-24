package com.civileng.marketplace.web.common.client;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QuotasTest {

    private final EntitlementsClient client = mock(EntitlementsClient.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:00Z"));
    private final AtomicReference<String> tenant = new AtomicReference<>("acme");
    private final Quotas quotas;

    QuotasTest() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(inv -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        quotas = new Quotas(client, tenant::get, clock);
    }

    private static EntitlementsClient.Entitlements limits(Map<String, Long> limits) {
        return new EntitlementsClient.Entitlements("acme", "starter", "Starter", "ACTIVE", Set.of(), limits);
    }

    @Test
    void refusesTheUnitThatWouldPassTheLimit() {
        when(client.mine()).thenReturn(limits(Map.of("bookings.monthly", 3L)));
        quotas.require("bookings.monthly", 2, "bookings a month");
        assertThatThrownBy(() -> quotas.require("bookings.monthly", 3, "bookings a month"))
                .isInstanceOf(QuotaExceededException.class).hasMessageContaining("allows 3 bookings a month");
    }

    @Test
    void absentMeansUnlimited() {
        when(client.mine()).thenReturn(limits(Map.of()));
        quotas.require("bookings.monthly", 1_000_000, "bookings a month");
    }

    @Test
    void cachesPerTenantForThirtySecondsSoUpgradesLandQuickly() {
        when(client.mine()).thenReturn(limits(Map.of("bookings.monthly", 3L)), limits(Map.of("bookings.monthly", 30L)));
        assertThat(quotas.limit("bookings.monthly")).hasValue(3);
        now.set(now.get().plus(Duration.ofSeconds(20)));
        assertThat(quotas.limit("bookings.monthly")).hasValue(3);
        now.set(now.get().plus(Duration.ofSeconds(15)));
        assertThat(quotas.limit("bookings.monthly")).hasValue(30);
        verify(client, times(2)).mine();
    }

    @Test
    void failsOpenWhenEntitlementsCannotBeRead() {
        when(client.mine()).thenThrow(new RuntimeException("tenant-service down"));
        assertThat(quotas.limit("bookings.monthly")).isEmpty();
        quotas.require("bookings.monthly", 999, "bookings a month");
    }

    @Test
    void noTenantNoCheck() {
        tenant.set(null);
        assertThat(quotas.limit("bookings.monthly")).isEmpty();
        verifyNoInteractions(client);
    }
}
