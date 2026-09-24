package com.civileng.marketplace.media.storage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** The operations this service needs from an S3-compatible store. */
public interface ObjectStorage {

    /** Creates the buckets if missing and makes the public one anonymously readable. */
    void ensureBuckets();

    /**
     * A browser upload form bound to exactly this key, content type and size range. The store
     * itself refuses anything else, so a client cannot swap the file or exceed the limit after
     * the slot was issued.
     */
    PresignedPost presignPost(String bucket, String key, String contentType, long maxBytes, Duration ttl);

    Optional<StoredObject> stat(String bucket, String key);

    /** The first {@code length} bytes, for checking the file's real type. */
    byte[] readHead(String bucket, String key, int length);

    String presignGet(String bucket, String key, Duration ttl, String downloadFilename);

    /** The permanent URL of an object in the public bucket. */
    String publicUrl(String bucket, String key);

    void delete(String bucket, String key);

    record PresignedPost(String url, Map<String, String> fields) { }

    record StoredObject(long size, String contentType) { }
}
