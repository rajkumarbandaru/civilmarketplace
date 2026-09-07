package com.civileng.marketplace.search.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class UserProfileClientFallbackFactory implements FallbackFactory<UserProfileClient> {

    @Override
    public UserProfileClient create(Throwable cause) {
        log.warn("user-service unavailable during reindex: {}", cause.getMessage());
        return (page, size) -> Map.of();
    }
}
