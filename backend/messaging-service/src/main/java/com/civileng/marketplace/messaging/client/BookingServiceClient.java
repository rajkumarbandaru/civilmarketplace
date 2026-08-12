package com.civileng.marketplace.messaging.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service", contextId = "bookingMessagingClient",
        path = "/api/v1/bookings", fallbackFactory = BookingServiceClientFallbackFactory.class)
public interface BookingServiceClient {

    @GetMapping("/{bookingId}")
    BookingDto getBooking(@PathVariable("bookingId") Long bookingId);
}
