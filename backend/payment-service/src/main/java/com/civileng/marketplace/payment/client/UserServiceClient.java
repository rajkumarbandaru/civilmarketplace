package com.civileng.marketplace.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "auth-service", path = "/api/v1/auth/admin", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/users/{userId}/name")
    ResponseEntity<Map<String, Object>> getUserName(@PathVariable("userId") Long userId);
}
