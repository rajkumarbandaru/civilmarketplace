package com.civileng.marketplace.media.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * A file as other services see it: {@link MediaDTO} plus its uploader, which a service needs to
 * check that the user attaching a file is the one who uploaded it. Never sent to browsers.
 * Mirrors {@code web-common}'s {@code MediaRef}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InternalMediaView(
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
