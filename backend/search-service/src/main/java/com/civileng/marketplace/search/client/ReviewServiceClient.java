package com.civileng.marketplace.search.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "review-service", contextId = "reviewSearchClient",
        path = "/api/v1/profiles", fallbackFactory = ReviewServiceClientFallbackFactory.class)
public interface ReviewServiceClient {

    @GetMapping("/{userId}/rating-summary")
    Map<String, Object> getRatingSummary(@PathVariable("userId") Long userId);
}
