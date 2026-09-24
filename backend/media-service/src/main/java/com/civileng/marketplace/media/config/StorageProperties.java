package com.civileng.marketplace.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where files are stored, bound from {@code media.storage.*}.
 *
 * <p>Two endpoints because the service and the browser reach the store by different names: inside
 * the network it is {@code http://minio:9000}, from a browser it is whatever host is published for
 * it. A signed URL is only valid for the host it was signed for, so the browser-facing ones must be
 * signed against {@link #publicEndpoint} — never the internal name the browser cannot resolve.
 *
 * <p>Pointing both at {@code https://s3.<region>.amazonaws.com} (or any S3-compatible store) moves
 * the platform off MinIO with no code change.
 */
@ConfigurationProperties(prefix = "media.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String region,
        String publicBucket,
        String privateBucket,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        /** How long a slot may stay un-uploaded before the sweep removes it. */
        Duration pendingTtl
) {
    public StorageProperties {
        region = region == null || region.isBlank() ? "us-east-1" : region;
        publicBucket = publicBucket == null ? "civeng-public" : publicBucket;
        privateBucket = privateBucket == null ? "civeng-private" : privateBucket;
        uploadUrlTtl = uploadUrlTtl == null ? Duration.ofMinutes(10) : uploadUrlTtl;
        downloadUrlTtl = downloadUrlTtl == null ? Duration.ofMinutes(5) : downloadUrlTtl;
        pendingTtl = pendingTtl == null ? Duration.ofHours(24) : pendingTtl;
        publicEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
