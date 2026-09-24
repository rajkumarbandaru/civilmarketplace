package com.civileng.marketplace.web.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * media-service's internal read, for services that own the rule of who may see a file (project
 * membership, KYC reviewers). Behind {@code /api/v1/media/internal}, which the gateway refuses from
 * outside. No fallback: a file that cannot be checked must not be accepted — use
 * {@link MediaReferences}, which turns the failures into answers a controller can return.
 */
@FeignClient(name = "media-service", contextId = "mediaClient", path = "/api/v1/media/internal")
public interface MediaClient {

    @GetMapping("/{mediaId}")
    MediaRef get(@PathVariable("mediaId") String mediaId, @RequestParam("onBehalfOf") Long onBehalfOf);
}
