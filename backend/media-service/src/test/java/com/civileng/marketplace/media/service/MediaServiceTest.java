package com.civileng.marketplace.media.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.media.config.StorageProperties;
import com.civileng.marketplace.media.dto.MediaDTO;
import com.civileng.marketplace.media.dto.UploadRequest;
import com.civileng.marketplace.media.dto.UploadTicket;
import com.civileng.marketplace.media.model.MediaObject;
import com.civileng.marketplace.media.model.MediaPurpose;
import com.civileng.marketplace.media.model.MediaStatus;
import com.civileng.marketplace.media.repository.MediaObjectRepository;
import com.civileng.marketplace.media.storage.ObjectStorage;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final Map<String, MediaObject> rows = new HashMap<>();
    private final MediaObjectRepository repository = mock(MediaObjectRepository.class);
    private final FakeStorage storage = new FakeStorage();
    private final AuditPublisher audit = mock(AuditPublisher.class);
    private final StorageProperties props = new StorageProperties("http://minio:9000", "http://localhost:9000",
            "k", "s", null, null, null, null, null, null);
    private MediaService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            MediaObject m = inv.getArgument(0);
            rows.put(m.getId(), m);
            return m;
        });
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<String>getArgument(0))));
        service = new MediaService(repository, storage, props, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set("acme");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private UploadTicket start(MediaPurpose purpose, String type, long size) {
        return service.requestUpload(7L, "CUSTOMER", new UploadRequest(purpose, "photo.jpg", type, size));
    }

    @Test
    void issuesATenantScopedSlotInTheBucketThePurposeDictates() {
        UploadTicket avatar = start(MediaPurpose.AVATAR, "image/jpeg", 100);
        UploadTicket kyc = start(MediaPurpose.KYC_DOCUMENT, "application/pdf", 100);

        MediaObject a = rows.get(avatar.mediaId());
        assertThat(a.getBucket()).isEqualTo("civeng-public");
        assertThat(a.getObjectKey()).startsWith("tenants/acme/avatar/2026/09/").endsWith(".jpg");
        assertThat(a.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(rows.get(kyc.mediaId()).getBucket()).isEqualTo("civeng-private");
        assertThat(avatar.maxBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(avatar.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(storage.lastPostMaxBytes).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void refusesWrongTypeOversizeAndUnauthorisedUploaders() {
        assertThatThrownBy(() -> start(MediaPurpose.AVATAR, "image/svg+xml", 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> start(MediaPurpose.AVATAR, "image/png", 6L * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 MB");
        assertThatThrownBy(() -> start(MediaPurpose.CATEGORY_IMAGE, "image/png", 100))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requestUpload(9L, "ADMIN",
                new UploadRequest(MediaPurpose.TENANT_LOGO, "l.png", "image/png", 10L)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requestUpload(null, null,
                new UploadRequest(MediaPurpose.AVATAR, "a.png", "image/png", 10L)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rows).isEmpty();
    }

    @Test
    void completeMarksAVerifiedFileReadyWithAPermanentPublicUrl() {
        UploadTicket t = start(MediaPurpose.AVATAR, "image/jpeg", FileSignatureTest.JPEG.length);
        storage.put(rows.get(t.mediaId()), FileSignatureTest.JPEG);

        MediaDTO dto = service.complete(7L, t.mediaId());

        assertThat(dto.status()).isEqualTo("READY");
        assertThat(dto.url()).startsWith("http://localhost:9000/civeng-public/tenants/acme/avatar/");
        assertThat(dto.urlExpiresAt()).isNull();
        assertThat(service.complete(7L, t.mediaId()).id()).isEqualTo(t.mediaId());
        verify(audit).publish(any());
    }

    @Test
    void completeRejectsAndDeletesAFileWhoseBytesAreNotWhatWasDeclared() {
        byte[] html = "<html><script>alert(1)</script>".getBytes();
        UploadTicket t = start(MediaPurpose.AVATAR, "image/jpeg", html.length);
        storage.put(rows.get(t.mediaId()), html);

        assertThatThrownBy(() -> service.complete(7L, t.mediaId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not match its type");
        assertThat(rows.get(t.mediaId()).getStatus()).isEqualTo(MediaStatus.REJECTED);
        assertThat(storage.objects).isEmpty();
        assertThatThrownBy(() -> service.get(7L, "CUSTOMER", t.mediaId())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void completeRejectsASizeMismatchAndWaitsForAMissingUpload() {
        UploadTicket missing = start(MediaPurpose.AVATAR, "image/jpeg", 6);
        assertThatThrownBy(() -> service.complete(7L, missing.mediaId()))
                .hasMessageContaining("not been uploaded");
        assertThat(rows.get(missing.mediaId()).getStatus()).isEqualTo(MediaStatus.PENDING);

        UploadTicket t = start(MediaPurpose.AVATAR, "image/jpeg", 999);
        storage.put(rows.get(t.mediaId()), FileSignatureTest.JPEG);
        assertThatThrownBy(() -> service.complete(7L, t.mediaId())).hasMessageContaining("declared size");
        assertThat(rows.get(t.mediaId()).getStatus()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    void onlyTheUploaderCanCompleteTheirSlot() {
        UploadTicket t = start(MediaPurpose.AVATAR, "image/jpeg", FileSignatureTest.JPEG.length);
        storage.put(rows.get(t.mediaId()), FileSignatureTest.JPEG);
        assertThatThrownBy(() -> service.complete(8L, t.mediaId())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void privateFilesAreOwnerOrStaffOnlyWithShortLivedAuditedLinks() {
        UploadTicket t = start(MediaPurpose.KYC_DOCUMENT, "application/pdf", FileSignatureTest.PDF.length);
        storage.put(rows.get(t.mediaId()), FileSignatureTest.PDF);
        service.complete(7L, t.mediaId());

        MediaDTO own = service.get(7L, "CUSTOMER", t.mediaId());
        assertThat(own.url()).startsWith("signed:");
        assertThat(own.urlExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(service.get(1L, "ADMIN", t.mediaId()).url()).startsWith("signed:");
        assertThatThrownBy(() -> service.get(8L, "CUSTOMER", t.mediaId())).isInstanceOf(NoSuchElementException.class);

        ArgumentCaptor<AuditEventMessage> events = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(audit, org.mockito.Mockito.times(3)).publish(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEventMessage::getAction)
                .containsExactly(AuditAction.CREATE, AuditAction.READ, AuditAction.READ);
    }

    @Test
    void deleteIsOwnerOrStaffAndRemovesTheObject() {
        UploadTicket t = start(MediaPurpose.PORTFOLIO, "image/png", FileSignatureTest.PNG.length);
        storage.put(rows.get(t.mediaId()), FileSignatureTest.PNG);
        service.complete(7L, t.mediaId());

        assertThatThrownBy(() -> service.delete(8L, "CUSTOMER", t.mediaId())).isInstanceOf(NoSuchElementException.class);
        service.delete(7L, "CUSTOMER", t.mediaId());

        assertThat(rows.get(t.mediaId()).getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(storage.objects).isEmpty();
        assertThatThrownBy(() -> service.delete(7L, "CUSTOMER", t.mediaId())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void auditOutageDoesNotFailTheUpload() {
        doThrow(new RuntimeException("kafka down")).when(audit).publish(any());
        UploadTicket t = start(MediaPurpose.AVATAR, "image/jpeg", FileSignatureTest.JPEG.length);
        storage.put(rows.get(t.mediaId()), FileSignatureTest.JPEG);
        assertThat(service.complete(7L, t.mediaId()).status()).isEqualTo("READY");
    }

    @Test
    void sweepRemovesStalePendingSlots() {
        MediaObject stale = MediaObject.builder().id("old").ownerUserId(7L).purpose(MediaPurpose.AVATAR)
                .bucket("civeng-public").objectKey("k").status(MediaStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 9, 1, 0, 0)).build();
        when(repository.findTop500ByStatusAndCreatedAtBefore(MediaStatus.PENDING, LocalDateTime.of(2026, 9, 23, 10, 0)))
                .thenReturn(new ArrayList<>(List.of(stale)));

        assertThat(service.sweepAbandoned()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(MediaStatus.DELETED);
    }

    @Test
    void sanitisesDisplayedFilenames() {
        assertThat(MediaService.sanitizeFilename("C:\\Users\\x\\..\\evil\".pdf")).isEqualTo("evil.pdf");
        assertThat(MediaService.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(MediaService.sanitizeFilename("/")).isEqualTo("file");
    }

    @Test
    void getRefusesPendingFiles() {
        UploadTicket t = start(MediaPurpose.AVATAR, "image/jpeg", 6);
        assertThatThrownBy(() -> service.get(7L, "CUSTOMER", t.mediaId())).isInstanceOf(NoSuchElementException.class);
        verify(audit, never()).publish(any());
    }

    /** An in-memory S3: enough to exercise the verify step against real bytes. */
    static final class FakeStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        long lastPostMaxBytes;

        void put(MediaObject m, byte[] data) {
            objects.put(m.getBucket() + "/" + m.getObjectKey(), data);
        }

        @Override public void ensureBuckets() { }

        @Override
        public PresignedPost presignPost(String bucket, String key, String contentType, long maxBytes, Duration ttl) {
            lastPostMaxBytes = maxBytes;
            return new PresignedPost("http://localhost:9000/" + bucket, Map.of("key", key));
        }

        @Override
        public Optional<StoredObject> stat(String bucket, String key) {
            byte[] data = objects.get(bucket + "/" + key);
            return data == null ? Optional.empty() : Optional.of(new StoredObject(data.length, null));
        }

        @Override
        public byte[] readHead(String bucket, String key, int length) {
            byte[] data = objects.get(bucket + "/" + key);
            return Arrays.copyOf(data, Math.min(length, data.length));
        }

        @Override
        public String presignGet(String bucket, String key, Duration ttl, String downloadFilename) {
            return "signed:" + bucket + "/" + key;
        }

        @Override
        public String publicUrl(String bucket, String key) {
            return "http://localhost:9000/" + bucket + "/" + key;
        }

        @Override
        public void delete(String bucket, String key) {
            objects.remove(bucket + "/" + key);
        }
    }
}
