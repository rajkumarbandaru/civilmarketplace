package com.civileng.marketplace.web.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Reads one booking by id, from booking-service.
 *
 * <p>Replaces the identical {@code BookingServiceClient} interfaces in review-service and
 * messaging-service. Named for the lookup rather than the callee, because two other interfaces
 * called {@code BookingServiceClient} still exist and do entirely different things —
 * project-service lists a project's bookings, search-service reads the service catalogue.
 */
@FeignClient(name = "booking-service", contextId = "bookingLookupClient",
        path = "/api/v1/bookings", fallbackFactory = BookingLookupClientFallbackFactory.class)
public interface BookingLookupClient {

    @GetMapping("/{bookingId}")
    BookingDto getBooking(@PathVariable("bookingId") Long bookingId);
}
