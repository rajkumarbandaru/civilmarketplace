package com.civileng.marketplace.booking.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("Auth-service unavailable, using fallback for user name resolution: {}", cause.getMessage());
        return new UserServiceClient() {
            @Override
            public ResponseEntity<Map<String, Object>> getUserName(Long userId) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "userId", userId,
                        "name", "User #" + userId,
                        "exists", false
                ));
            }
        };
    }
}
