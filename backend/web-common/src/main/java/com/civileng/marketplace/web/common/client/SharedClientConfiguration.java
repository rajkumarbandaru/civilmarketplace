package com.civileng.marketplace.web.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans for the shared service-to-service clients.
 *
 * <p>Fallback factories are declared here rather than annotated {@code @Component}: this package
 * sits outside every service's component scan, so a stereotype annotation would be silently
 * ignored and Feign would fail to resolve the fallback at runtime rather than at compile time.
 *
 * <p>{@code @ConditionalOnBean} on the client rather than an unconditional bean: a service that
 * does not scan this package has no client to inject, and an eagerly-declared resolver would fail
 * its startup rather than simply being absent.
 */
@Configuration
@ConditionalOnClass(FeignClient.class)
public class SharedClientConfiguration {

    /**
     * Feign resolves the factory named in {@code @FeignClient(fallbackFactory = ...)} from the
     * context, so it has to be a bean even though nothing injects it directly.
     */
    @Bean
    @ConditionalOnMissingBean
    public UserNameClientFallbackFactory userNameClientFallbackFactory() {
        return new UserNameClientFallbackFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public BookingLookupClientFallbackFactory bookingLookupClientFallbackFactory() {
        return new BookingLookupClientFallbackFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserDirectoryClientFallbackFactory userDirectoryClientFallbackFactory() {
        return new UserDirectoryClientFallbackFactory();
    }

    @Bean
    @ConditionalOnBean(UserNameClient.class)
    @ConditionalOnMissingBean
    public UserNameResolver userNameResolver(UserNameClient userNameClient) {
        return new UserNameResolver(userNameClient);
    }
}
