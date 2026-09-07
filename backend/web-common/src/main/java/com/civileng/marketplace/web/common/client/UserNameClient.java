package com.civileng.marketplace.web.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Resolves a user id to a display name, from auth-service.
 *
 * <p>One definition for what were two byte-identical {@code UserServiceClient} interfaces in
 * booking-service and payment-service. Named for what it does rather than which service it happens
 * to live in — the old name collided with three <em>unrelated</em> {@code UserServiceClient}
 * interfaces (search-service reads profiles, support-service reads material rates), which made the
 * codebase look more duplicated than it was and the real duplicate harder to spot.
 *
 * <p>Behind {@code /api/v1/auth/admin}, which {@code InternalOnlyPathFilter} keeps unreachable from
 * the edge — this is a service-to-service read and nothing else.
 */
@FeignClient(name = "auth-service", contextId = "userNameClient",
        path = "/api/v1/auth/admin", fallbackFactory = UserNameClientFallbackFactory.class)
public interface UserNameClient {

    @GetMapping("/users/{userId}/name")
    ResponseEntity<Map<String, Object>> getUserName(@PathVariable("userId") Long userId);
}
