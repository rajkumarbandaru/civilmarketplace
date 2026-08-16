package com.civileng.marketplace.support.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * No material rates rather than a failed answer. The assistant is told separately to price
 * materials as a labelled market assumption when the supplier list is missing, which is the honest
 * fallback — an estimate without site rates is still useful, one with invented ones is not.
 */
@Component
@Slf4j
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("[CivilAI] user-service unavailable, answering without supplier material rates: {}",
                cause.getMessage());
        return Map::of;
    }
}
