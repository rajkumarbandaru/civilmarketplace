package com.civileng.marketplace.review.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingServiceClientFallbackFactory implements FallbackFactory<BookingServiceClient> {

    @Override
    public BookingServiceClient create(Throwable cause) {
        log.warn("Booking-service unavailable, cannot verify booking for review: {}", cause.getMessage());
        return bookingId -> null;
    }
}
