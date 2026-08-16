package com.civileng.marketplace.support.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * An empty result rather than an exception: search-service being down should cost the answer its
 * site rate card, not the answer itself. The assistant is told separately what to say when the
 * card is missing, so the user learns the site figures are unavailable rather than seeing invented
 * ones.
 */
@Component
@Slf4j
public class SearchServiceClientFallbackFactory implements FallbackFactory<SearchServiceClient> {

    @Override
    public SearchServiceClient create(Throwable cause) {
        log.warn("[CivilAI] search-service unavailable, answering without site rates: {}", cause.getMessage());
        return (availableOnly, sort, page, size) -> Map.of();
    }
}
