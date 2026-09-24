package com.civileng.marketplace.media.config;

import com.civileng.marketplace.media.storage.MinioObjectStorage;
import com.civileng.marketplace.media.storage.ObjectStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@Slf4j
public class StorageConfig {

    @Bean
    public ObjectStorage objectStorage(StorageProperties properties) {
        return new MinioObjectStorage(properties);
    }

    /**
     * After startup rather than in the bean factory: a store that is still starting must not stop
     * the service from coming up. It is retried here once; uploads fail cleanly until it is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void prepareBuckets(ApplicationReadyEvent event) {
        try {
            event.getApplicationContext().getBean(ObjectStorage.class).ensureBuckets();
        } catch (RuntimeException e) {
            log.error("Object storage is not reachable yet; buckets not prepared", e);
        }
    }
}
