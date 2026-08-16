package com.civileng.marketplace.support.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Reads the registered supply side out of search-service, so the assistant can quote what this
 * platform's own providers actually charge instead of only guessing at market rates.
 *
 * <p>search-service is used rather than user-service because the profile index already carries the
 * role, city and hourly rate together — assembling the same view from user-service would mean
 * joining profiles against auth-service roles on every question.
 */
@FeignClient(name = "search-service", contextId = "aiSearchClient",
        path = "/api/v1/search", fallbackFactory = SearchServiceClientFallbackFactory.class)
public interface SearchServiceClient {

    @GetMapping("/profiles")
    Map<String, Object> searchProfiles(@RequestParam("availableOnly") boolean availableOnly,
                                       @RequestParam("sort") String sort,
                                       @RequestParam("page") int page,
                                       @RequestParam("size") int size);
}
