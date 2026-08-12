package com.civileng.marketplace.messaging.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingServiceClientFallbackFactory implements FallbackFactory<BookingServiceClient> {

    @Override
    public BookingServiceClient create(Throwable cause) {
        log.warn("booking-service unavailable, cannot verify booking for messaging: {}",
                cause.getMessage());
        return bookingId -> null;
    }
}
