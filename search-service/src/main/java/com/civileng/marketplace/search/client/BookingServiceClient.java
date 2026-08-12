package com.civileng.marketplace.search.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "booking-service", contextId = "bookingSearchClient",
        path = "/api/v1/bookings/admin", fallbackFactory = BookingServiceClientFallbackFactory.class)
public interface BookingServiceClient {

    @GetMapping("/categories")
    Map<String, Object> getCategories();
}
