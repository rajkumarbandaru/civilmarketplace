package com.civileng.marketplace.search.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "auth-service", contextId = "authSearchClient",
        path = "/api/v1/auth/admin", fallbackFactory = AuthServiceClientFallbackFactory.class)
public interface AuthServiceClient {

    @GetMapping("/users")
    Map<String, Object> getUsers(@RequestParam("page") int page,
                                 @RequestParam("size") int size);
}
