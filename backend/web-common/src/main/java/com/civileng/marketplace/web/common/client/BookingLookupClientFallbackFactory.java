package com.civileng.marketplace.web.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * Returns null when booking-service cannot answer.
 *
 * <p>Null and not an empty {@link BookingDto}: both callers use this to decide whether someone is
 * a party to a booking, and an empty booking would answer "no" to that question with the same
 * confidence as a real one. Null forces the caller to treat an outage as unknown rather than as a
 * denial — the same rule project-service's booking fallback already follows.
 *
 * <p>Declared as a bean by {@link SharedClientConfiguration}; this package is outside every
 * service's component scan, so {@code @Component} here would be silently ignored.
 */
@Slf4j
public class BookingLookupClientFallbackFactory implements FallbackFactory<BookingLookupClient> {

    @Override
    public BookingLookupClient create(Throwable cause) {
        log.warn("booking-service unavailable, cannot verify booking: {}", cause.getMessage());
        return bookingId -> null;
    }
}
