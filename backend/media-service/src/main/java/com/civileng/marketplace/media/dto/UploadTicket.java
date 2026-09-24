package com.civileng.marketplace.media.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Everything a browser needs to upload one file straight to storage: POST {@code fields} plus the
 * file (as the last form field, named {@code file}) to {@code uploadUrl}, then call complete.
 */
public record UploadTicket(
        String mediaId,
        String uploadUrl,
        Map<String, String> fields,
        long maxBytes,
        Instant expiresAt
) { }
