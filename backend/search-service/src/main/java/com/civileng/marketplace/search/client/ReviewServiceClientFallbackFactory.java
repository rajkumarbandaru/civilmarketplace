package com.civileng.marketplace.search.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ReviewServiceClientFallbackFactory implements FallbackFactory<ReviewServiceClient> {

    @Override
    public ReviewServiceClient create(Throwable cause) {
        log.warn("review-service unavailable during reindex, ratings default to 0: {}",
                cause.getMessage());
        return userId -> Map.of();
    }
}
