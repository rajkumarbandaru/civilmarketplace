package com.civileng.marketplace.project.client;

import com.civileng.marketplace.web.common.client.BookingDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "booking-service", path = "/api/v1/bookings",
        fallbackFactory = ProjectBookingsClientFallbackFactory.class)
public interface ProjectBookingsClient {

    @GetMapping("/project/{projectId}")
    List<BookingDto> getProjectBookings(@PathVariable("projectId") Long projectId);
}
