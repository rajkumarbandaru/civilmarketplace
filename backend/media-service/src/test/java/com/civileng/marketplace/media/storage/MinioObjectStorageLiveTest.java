package com.civileng.marketplace.media.storage;

import com.civileng.marketplace.media.config.StorageProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real MinIO, and only when {@code MEDIA_IT_ENDPOINT} points at one — for example
 * {@code docker compose up -d minio} then {@code MEDIA_IT_ENDPOINT=http://localhost:9000}. It checks
 * the things a mock cannot: that the store itself enforces the signed form's key, type and size,
 * and that the public bucket is readable anonymously while the private one is not.
 */
@EnabledIfEnvironmentVariable(named = "MEDIA_IT_ENDPOINT", matches = ".+")
class MinioObjectStorageLiveTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static StorageProperties props;
    private static MinioObjectStorage storage;

    @BeforeAll
    static void setUp() {
        String endpoint = System.getenv("MEDIA_IT_ENDPOINT");
        props = new StorageProperties(endpoint, endpoint,
                env("MEDIA_IT_ACCESS_KEY", "civeng-media"), env("MEDIA_IT_SECRET_KEY", "civeng-media-secret"),
                null, "it-public", "it-private", null, null, null);
        storage = new MinioObjectStorage(props);
        storage.ensureBuckets();
        storage.ensureBuckets(); // idempotent
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null ? fallback : v;
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Test
    void signedFormUploadThenPublicRead() throws Exception {
        String key = "tenants/it/avatar/" + UUID.randomUUID() + ".png";
        ObjectStorage.PresignedPost post = storage.presignPost("it-public", key, "image/png", 1024, Duration.ofMinutes(5));

        assertThat(upload(post, "image/png", PNG)).isBetween(200, 299);
        assertThat(storage.stat("it-public", key)).get().extracting(ObjectStorage.StoredObject::size).isEqualTo(12L);
        assertThat(storage.readHead("it-public", key, 8)).hasSize(8).startsWith((byte) 0x89, (byte) 'P');

        HttpResponse<byte[]> anon = HTTP.send(HttpRequest.newBuilder(URI.create(storage.publicUrl("it-public", key))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(anon.statusCode()).isEqualTo(200);
        assertThat(anon.body()).isEqualTo(PNG);

        storage.delete("it-public", key);
        assertThat(storage.stat("it-public", key)).isEmpty();
    }

    @Test
    void storeRefusesAnythingTheFormWasNotSignedFor() throws Exception {
        String key = "tenants/it/avatar/" + UUID.randomUUID() + ".png";
        ObjectStorage.PresignedPost post = storage.presignPost("it-public", key, "image/png", 8, Duration.ofMinutes(5));

        assertThat(upload(post, "text/html", PNG)).as("content type swapped").isEqualTo(403);
        assertThat(upload(post, "image/png", PNG)).as("over the size limit").isBetween(400, 403);
        assertThat(storage.stat("it-public", key)).isEmpty();
    }

    @Test
    void privateObjectsNeedASignedLink() throws Exception {
        String key = "tenants/it/kyc/" + UUID.randomUUID() + ".png";
        ObjectStorage.PresignedPost post = storage.presignPost("it-private", key, "image/png", 1024, Duration.ofMinutes(5));
        assertThat(upload(post, "image/png", PNG)).isBetween(200, 299);

        int anon = HTTP.send(HttpRequest.newBuilder(URI.create(storage.publicUrl("it-private", key))).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        assertThat(anon).isEqualTo(403);

        HttpResponse<byte[]> signed = HTTP.send(HttpRequest.newBuilder(
                URI.create(storage.presignGet("it-private", key, Duration.ofMinutes(1), "my id.png"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(signed.statusCode()).isEqualTo(200);
        assertThat(signed.body()).isEqualTo(PNG);
        assertThat(signed.headers().firstValue("Content-Disposition")).get().asString().contains("my%20id.png");
        storage.delete("it-private", key);
    }

    /** What the browser does: the signed fields, with the file as the last part. */
    private static int upload(ObjectStorage.PresignedPost post, String contentType, byte[] file) throws Exception {
        String boundary = "----it" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> f : post.fields().entrySet()) {
            String value = f.getKey().equals("Content-Type") ? contentType : f.getValue();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HTTP.send(HttpRequest.newBuilder(URI.create(post.url()))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
