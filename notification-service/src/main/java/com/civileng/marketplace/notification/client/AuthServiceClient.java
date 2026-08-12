package com.civileng.marketplace.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Read-only lookup used to resolve an announcement's audience. Calls
 * {@code AdminUserController} directly (service-to-service, not through the gateway), same
 * pattern admin-service and search-service already use against this endpoint.
 */
@FeignClient(name = "auth-service", contextId = "authNotificationClient", path = "/api/v1/auth/admin")
public interface AuthServiceClient {

    @GetMapping("/users")
    Map<String, Object> getUsers(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "200") int size,
                                  @RequestParam(required = false) String role,
                                  @RequestParam(required = false) String status);
}
