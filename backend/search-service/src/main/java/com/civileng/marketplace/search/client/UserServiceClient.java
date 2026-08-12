package com.civileng.marketplace.search.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-service", contextId = "userSearchClient",
        path = "/api/v1/users/admin", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/profiles")
    Map<String, Object> getProfiles(@RequestParam("page") int page,
                                    @RequestParam("size") int size);
}
