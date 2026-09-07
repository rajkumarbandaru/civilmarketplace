package com.civileng.marketplace.search.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ServiceCatalogueClientFallbackFactory implements FallbackFactory<ServiceCatalogueClient> {

    @Override
    public ServiceCatalogueClient create(Throwable cause) {
        log.warn("booking-service unavailable during reindex: {}", cause.getMessage());
        return Map::of;
    }
}
