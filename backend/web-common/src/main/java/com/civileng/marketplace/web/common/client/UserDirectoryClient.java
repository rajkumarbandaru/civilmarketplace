package com.civileng.marketplace.web.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Pages the user directory, from auth-service.
 *
 * <p>Replaces the {@code AuthServiceClient} interfaces in search-service and notification-service,
 * which called the same endpoint with different signatures — search-service's took page and size
 * only, notification-service's added role and status filters. The superset is what ships here;
 * search-service passes null for the two filters, which the endpoint already treats as "no filter".
 *
 * <p>Two callers, both bulk readers: search-service rebuilds its profile index from this, and
 * notification-service resolves an announcement's audience. Behind {@code /api/v1/auth/admin},
 * which {@code InternalOnlyPathFilter} keeps unreachable from the edge.
 */
@FeignClient(name = "auth-service", contextId = "userDirectoryClient",
        path = "/api/v1/auth/admin", fallbackFactory = UserDirectoryClientFallbackFactory.class)
public interface UserDirectoryClient {

    @GetMapping("/users")
    Map<String, Object> getUsers(@RequestParam("page") int page,
                                 @RequestParam("size") int size,
                                 @RequestParam(value = "role", required = false) String role,
                                 @RequestParam(value = "status", required = false) String status);
}
