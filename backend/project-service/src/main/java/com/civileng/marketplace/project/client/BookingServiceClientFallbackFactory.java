package com.civileng.marketplace.project.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Returning null rather than an empty list is deliberate: an empty list is indistinguishable from
 * "this project has no bookings", and the completion guard must not read a booking-service outage
 * as permission to complete a project. Callers treat null as "unknown" and refuse to guess.
 */
@Component
@Slf4j
public class BookingServiceClientFallbackFactory implements FallbackFactory<BookingServiceClient> {

    @Override
    public BookingServiceClient create(Throwable cause) {
        log.warn("booking-service unavailable, project booking data unknown: {}", cause.getMessage());
        return projectId -> null;
    }
}
