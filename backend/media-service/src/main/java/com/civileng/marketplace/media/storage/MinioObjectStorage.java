package com.civileng.marketplace.media.storage;

import com.civileng.marketplace.media.config.StorageProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link ObjectStorage} over the MinIO SDK, which speaks plain S3 and so also works against AWS.
 *
 * <p>Two clients: {@code client} does real I/O against the internal endpoint; {@code signer} is
 * only ever used to sign URLs for the public endpoint. The signer has its region set explicitly so
 * signing is pure computation — without it the SDK would look the region up over the network, from
 * inside the container, at a public hostname that may not resolve there.
 */
@Slf4j
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final MinioClient signer;
    private final StorageProperties props;

    public MinioObjectStorage(StorageProperties props) {
        this.props = props;
        this.client = MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .region(props.region())
                .build();
        this.signer = MinioClient.builder()
                .endpoint(props.publicEndpoint())
                .credentials(props.accessKey(), props.secretKey())
                .region(props.region())
                .build();
    }

    @Override
    public void ensureBuckets() {
        try {
            for (String bucket : new String[]{props.publicBucket(), props.privateBucket()}) {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).region(props.region()).build());
                    log.info("Created bucket {}", bucket);
                }
            }
            // Read-only, objects only: anonymous callers can fetch a file they have the URL of,
            // but cannot list the bucket and discover the rest.
            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                    "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}"""
                    .formatted(props.publicBucket());
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(props.publicBucket()).config(policy).build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare storage buckets", e);
        }
    }

    @Override
    public PresignedPost presignPost(String bucket, String key, String contentType, long maxBytes, Duration ttl) {
        try {
            PostPolicy policy = new PostPolicy(bucket, ZonedDateTime.now().plus(ttl));
            policy.addEqualsCondition("key", key);
            policy.addEqualsCondition("Content-Type", contentType);
            policy.addContentLengthRangeCondition(1, maxBytes);
            Map<String, String> fields = new HashMap<>(signer.getPresignedPostFormData(policy));
            // The SDK signs the conditions but leaves the matching form fields to the caller.
            fields.put("key", key);
            fields.put("Content-Type", contentType);
            return new PresignedPost(stripSlash(props.publicEndpoint()) + "/" + bucket, fields);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign upload", e);
        }
    }

    @Override
    public Optional<StoredObject> stat(String bucket, String key) {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return Optional.of(new StoredObject(stat.size(), stat.contentType()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IllegalStateException("Could not read object metadata", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read object metadata", e);
        }
    }

    @Override
    public byte[] readHead(String bucket, String key, int length) {
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).offset(0L).length((long) length).build())) {
            return in.readNBytes(length);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read object", e);
        }
    }

    @Override
    public String presignGet(String bucket, String key, Duration ttl, String downloadFilename) {
        try {
            Map<String, String> query = new HashMap<>();
            if (downloadFilename != null) {
                query.put("response-content-disposition", "inline; filename*=UTF-8''"
                        + URLEncoder.encode(downloadFilename, StandardCharsets.UTF_8).replace("+", "%20"));
            }
            return signer.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(key)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .extraQueryParams(query)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign download", e);
        }
    }

    @Override
    public String publicUrl(String bucket, String key) {
        return stripSlash(props.publicEndpoint()) + "/" + bucket + "/" + key;
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not delete object", e);
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
