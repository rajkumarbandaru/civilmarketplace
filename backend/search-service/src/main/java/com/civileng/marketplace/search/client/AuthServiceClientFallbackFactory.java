package com.civileng.marketplace.search.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class AuthServiceClientFallbackFactory implements FallbackFactory<AuthServiceClient> {

    @Override
    public AuthServiceClient create(Throwable cause) {
        log.warn("auth-service unavailable during reindex: {}", cause.getMessage());
        return (page, size) -> Map.of();
    }
}
