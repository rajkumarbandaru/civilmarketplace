package com.civileng.marketplace.search.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "booking-service", contextId = "bookingSearchClient",
        path = "/api/v1/bookings/admin", fallbackFactory = ServiceCatalogueClientFallbackFactory.class)
public interface ServiceCatalogueClient {

    @GetMapping("/categories")
    Map<String, Object> getCategories();
}
