package com.civileng.marketplace.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-service", path = "/api/v1/users")
public interface UserServiceClient {

    @GetMapping("/admin/profiles")
    ResponseEntity<Map<String, Object>> getAllProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @GetMapping("/admin/profiles/{userId}")
    ResponseEntity<Map<String, Object>> getProfileByUserId(@PathVariable Long userId);

    @GetMapping("/admin/stats")
    ResponseEntity<Map<String, Object>> getUserProfileStats();
}
