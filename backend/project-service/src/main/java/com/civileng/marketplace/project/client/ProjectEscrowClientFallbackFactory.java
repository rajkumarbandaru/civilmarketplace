package com.civileng.marketplace.project.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** Null means "unknown", the same convention the booking client uses — never an empty list. */
@Component
@Slf4j
public class ProjectEscrowClientFallbackFactory implements FallbackFactory<ProjectEscrowClient> {

    @Override
    public ProjectEscrowClient create(Throwable cause) {
        log.warn("payment-service unavailable, project escrow data unknown: {}", cause.getMessage());
        return projectId -> null;
    }
}
