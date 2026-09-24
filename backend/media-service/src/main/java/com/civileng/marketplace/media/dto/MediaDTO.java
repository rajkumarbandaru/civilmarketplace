package com.civileng.marketplace.media.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A file as callers see it.
 *
 * <p>{@code url} is permanent for public files — store it wherever the image is shown. For private
 * files it is a signed link that stops working at {@code urlExpiresAt}; store the {@code id} and
 * ask for a fresh link each time the file is opened.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaDTO(
        String id,
        String purpose,
        String visibility,
        String status,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String url,
        Instant urlExpiresAt,
        LocalDateTime createdAt
) { }
