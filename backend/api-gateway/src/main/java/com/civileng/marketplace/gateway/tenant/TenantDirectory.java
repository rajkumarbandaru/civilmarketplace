package com.civileng.marketplace.gateway.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host → tenant lookups, cached.
 *
 * <p>Every single request needs this answer, so an uncached call would put a tenant-service round
 * trip in front of the whole platform. The cache is deliberately short-lived: suspending a tenant
 * has to actually stop traffic, and a minute is the longest that may lag.
 */
@Slf4j
@Component
public class TenantDirectory {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final WebClient webClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TenantDirectory(WebClient.Builder loadBalancedWebClientBuilder,
                           @Value("${platform.tenant.service-uri:lb://tenant-service}") String uri) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl(uri).build();
    }

    public Mono<TenantDescriptor> resolve(String host) {
        CacheEntry cached = cache.get(host);
        if (cached != null && cached.isFresh()) {
            return Mono.just(cached.descriptor());
        }

        return webClient.get()
                .uri(builder -> builder.path("/api/v1/tenant-resolution")
                        .queryParam("host", host).build())
                .retrieve()
                .bodyToMono(TenantDescriptor.class)
                .doOnNext(descriptor ->
                        cache.put(host, new CacheEntry(descriptor, Instant.now().plus(TTL))))
                .onErrorResume(e -> {
                    // A stale entry beats a platform-wide outage: if tenant-service is down,
                    // hosts we already know keep serving rather than every request failing.
                    if (cached != null) {
                        log.warn("Tenant lookup for '{}' failed ({}), serving stale entry",
                                host, e.getMessage());
                        return Mono.just(cached.descriptor());
                    }
                    log.warn("Tenant lookup for '{}' failed: {}", host, e.getMessage());
                    return Mono.empty();
                });
    }

    public void evict(String host) {
        cache.remove(host);
    }

    private record CacheEntry(TenantDescriptor descriptor, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
