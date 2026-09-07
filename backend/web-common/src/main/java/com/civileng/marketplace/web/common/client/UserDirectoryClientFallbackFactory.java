package com.civileng.marketplace.web.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Map;

/**
 * Answers with an empty map when auth-service cannot be reached.
 *
 * <p>Both callers page until the directory is exhausted, so an empty page reads as "no more users"
 * and ends the loop — a partial index or a partial announcement audience, not a failed request.
 * That is the right trade for search-service, whose index is a replica rebuilt on a schedule.
 * It is worth knowing for notification-service: an announcement sent during an auth-service outage
 * reaches whoever was already paged and no one after, rather than failing loudly.
 *
 * <p>Declared as a bean by {@link SharedClientConfiguration} — this package is outside every
 * service's component scan, so {@code @Component} here would be silently ignored.
 */
@Slf4j
public class UserDirectoryClientFallbackFactory implements FallbackFactory<UserDirectoryClient> {

    @Override
    public UserDirectoryClient create(Throwable cause) {
        log.warn("auth-service unavailable, returning an empty user page: {}", cause.getMessage());
        return (page, size, role, status) -> Map.of();
    }
}
