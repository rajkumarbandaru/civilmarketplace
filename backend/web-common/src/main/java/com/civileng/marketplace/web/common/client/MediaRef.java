package com.civileng.marketplace.web.common.client;

import java.time.Instant;

/**
 * A stored file as media-service describes it to other services. Unlike the browser-facing shape,
 * it names the owner, so the caller can check that a file being attached was uploaded by the
 * person attaching it.
 *
 * <p>{@code url} is permanent for PUBLIC files and a short-lived signed link for PRIVATE ones:
 * keep the {@code id}, and ask again for a fresh link whenever a private file is opened.
 */
public record MediaRef(
        String id,
        String purpose,
        String visibility,
        Long ownerUserId,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String url,
        Instant urlExpiresAt
) { }
