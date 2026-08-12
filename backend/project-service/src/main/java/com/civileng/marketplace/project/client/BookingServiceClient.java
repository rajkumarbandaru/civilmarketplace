package com.civileng.marketplace.project.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "booking-service", path = "/api/v1/bookings",
        fallbackFactory = BookingServiceClientFallbackFactory.class)
public interface BookingServiceClient {

    @GetMapping("/project/{projectId}")
    List<BookingDto> getProjectBookings(@PathVariable("projectId") Long projectId);
}
