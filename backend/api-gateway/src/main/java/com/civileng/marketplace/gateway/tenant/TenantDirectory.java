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

    /**
     * A live tenant's modules follow its plan: an upgrade must show up quickly (architecture 08 §7
     * caps local staleness at 30 s).
     */
    private static final Duration TTL = Duration.ofSeconds(30);

    /**
     * A tenant that is not live is usually on its way to being live (DRAFT, PROVISIONING): its
     * owner opens the invitation link seconds after it goes ACTIVE, and a minute-old "draft"
     * answer would turn them away. Not-live answers are therefore only trusted briefly.
     */
    static final Duration NOT_LIVE_TTL = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TenantDirectory(WebClient.Builder loadBalancedWebClientBuilder,
                           @Value("${platform.tenant.service-uri:lb://tenant-service}") String uri) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl(uri).build();
    }

    /** Replaceable in tests, so cache expiry can be exercised without waiting it out. */
    java.time.Clock clock = java.time.Clock.systemUTC();

    public Mono<TenantDescriptor> resolve(String host) {
        CacheEntry cached = cache.get(host);
        if (cached != null && cached.isFresh(clock.instant())) {
            return Mono.just(cached.descriptor());
        }

        return webClient.get()
                .uri(builder -> builder.path("/api/v1/tenant-resolution")
                        .queryParam("host", host).build())
                .retrieve()
                .bodyToMono(TenantDescriptor.class)
                .doOnNext(descriptor ->
                        cache.put(host, new CacheEntry(descriptor, clock.instant().plus(ttlFor(descriptor)))))
                .onErrorResume(e -> {
                    // An answer, not an outage: tenant-service says no tenant serves this host
                    // (any more — a removed custom domain, an archived tenant). Forget it; serving
                    // the stale entry here would route a released host forever.
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException r
                            && r.getStatusCode().is4xxClientError()) {
                        cache.remove(host);
                        return Mono.empty();
                    }
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

    static Duration ttlFor(TenantDescriptor descriptor) {
        return descriptor.isActive() ? TTL : NOT_LIVE_TTL;
    }

    public void evict(String host) {
        cache.remove(host);
    }

    private record CacheEntry(TenantDescriptor descriptor, Instant expiresAt) {
        boolean isFresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
