package com.civileng.marketplace.media.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.media.config.StorageProperties;
import com.civileng.marketplace.media.dto.InternalMediaView;
import com.civileng.marketplace.media.dto.MediaDTO;
import com.civileng.marketplace.media.dto.UploadRequest;
import com.civileng.marketplace.media.dto.UploadTicket;
import com.civileng.marketplace.media.model.MediaObject;
import com.civileng.marketplace.media.model.MediaPurpose;
import com.civileng.marketplace.media.model.MediaStatus;
import com.civileng.marketplace.media.model.Visibility;
import com.civileng.marketplace.media.repository.MediaObjectRepository;
import com.civileng.marketplace.media.storage.ObjectStorage;
import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.web.common.StaffRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The upload lifecycle.
 *
 * <ol>
 *   <li>{@link #requestUpload}: checks the purpose's rules against what the client declares and
 *       issues a signed form bound to one key, one type and the purpose's size limit.</li>
 *   <li>The browser uploads straight to storage. The bytes never pass through this service.</li>
 *   <li>{@link #complete}: re-reads what actually landed — its size and its first bytes — and only
 *       then marks the file READY. Anything that does not match is deleted.</li>
 * </ol>
 *
 * Only READY files ever have a URL handed out.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MediaService {

    private static final String SOURCE = "media-service";
    private static final String ENTITY = "MEDIA";

    private final MediaObjectRepository repository;
    private final ObjectStorage storage;
    private final StorageProperties props;
    private final AuditPublisher auditPublisher;
    private final Clock clock;

    @Transactional
    public UploadTicket requestUpload(Long userId, String role, UploadRequest request) {
        requireUser(userId);
        MediaPurpose purpose = request.purpose();
        if (!purpose.uploaders().allows(role)) {
            throw new AccessDeniedException("You cannot upload files of this kind");
        }
        String contentType = request.contentType().trim().toLowerCase();
        if (!purpose.contentTypes().contains(contentType)) {
            throw new IllegalArgumentException("This file type is not allowed here. Allowed: "
                    + String.join(", ", purpose.contentTypes().stream().sorted().toList()));
        }
        if (request.sizeBytes() > purpose.maxBytes()) {
            throw new IllegalArgumentException("File is too large. The limit is "
                    + purpose.maxBytes() / (1024 * 1024) + " MB");
        }

        String id = UUID.randomUUID().toString();
        String bucket = bucketFor(purpose.visibility());
        LocalDateTime now = LocalDateTime.now(clock);
        String key = "tenants/%s/%s/%d/%02d/%s.%s".formatted(
                TenantContext.require(), purpose.slug(), now.getYear(), now.getMonthValue(), id,
                FileSignature.extensionFor(contentType));

        repository.save(MediaObject.builder()
                .id(id)
                .ownerUserId(userId)
                .purpose(purpose)
                .visibility(purpose.visibility())
                .bucket(bucket)
                .objectKey(key)
                .originalFilename(sanitizeFilename(request.filename()))
                .contentType(contentType)
                .declaredSize(request.sizeBytes())
                .status(MediaStatus.PENDING)
                .createdAt(now)
                .build());

        ObjectStorage.PresignedPost post = storage.presignPost(
                bucket, key, contentType, purpose.maxBytes(), props.uploadUrlTtl());
        return new UploadTicket(id, post.url(), post.fields(), purpose.maxBytes(),
                clock.instant().plus(props.uploadUrlTtl()));
    }

    /** Verifies what was uploaded. Safe to call twice: a file already READY is simply returned. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public MediaDTO complete(Long userId, String mediaId) {
        MediaObject media = find(mediaId);
        if (!media.getOwnerUserId().equals(userId)) {
            // Not 403: whether someone else's upload exists is not this caller's business.
            throw new NoSuchElementException("File not found");
        }
        if (media.getStatus() == MediaStatus.READY) {
            return toDto(media);
        }
        if (media.getStatus() != MediaStatus.PENDING) {
            throw new NoSuchElementException("File not found");
        }

        ObjectStorage.StoredObject stored = storage.stat(media.getBucket(), media.getObjectKey())
                .orElseThrow(() -> new IllegalArgumentException("The file has not been uploaded yet"));

        String problem = null;
        if (stored.size() > media.getPurpose().maxBytes()) {
            problem = "File is larger than allowed";
        } else if (stored.size() != media.getDeclaredSize()) {
            problem = "Uploaded file does not match the declared size";
        } else {
            String actual = FileSignature
                    .detect(storage.readHead(media.getBucket(), media.getObjectKey(), FileSignature.HEAD_BYTES))
                    .orElse(null);
            if (!media.getContentType().equals(actual)) {
                problem = "File content does not match its type";
            }
        }

        if (problem != null) {
            reject(media, problem);
            throw new IllegalArgumentException(problem);
        }

        media.setStatus(MediaStatus.READY);
        media.setSizeBytes(stored.size());
        media.setCompletedAt(LocalDateTime.now(clock));
        repository.save(media);
        audit(userId, AuditAction.CREATE, media, media.getPurpose().name());
        return toDto(media);
    }

    /**
     * A READY file with a usable URL. Public files are readable by any signed-in user; private
     * ones only by their owner and by staff, and every private read is logged — these are KYC
     * papers and project documents, whose access DPDP requires an audit trail of.
     */
    @Transactional(readOnly = true)
    public MediaDTO get(Long userId, String role, String mediaId) {
        MediaObject media = findReady(mediaId);
        if (media.getVisibility() == Visibility.PRIVATE) {
            if (!media.getOwnerUserId().equals(userId) && !StaffRoles.isStaff(role)) {
                throw new NoSuchElementException("File not found");
            }
            audit(userId, AuditAction.READ, media, null);
        }
        return toDto(media);
    }

    /**
     * For other services that have already decided the caller may see the file — project-service
     * for a project member's document, for example. Reachable only inside the network.
     */
    @Transactional(readOnly = true)
    public InternalMediaView getForService(String mediaId, Long onBehalfOf) {
        MediaObject media = findReady(mediaId);
        if (media.getVisibility() == Visibility.PRIVATE) {
            audit(onBehalfOf, AuditAction.READ, media, "via service");
        }
        MediaDTO dto = toDto(media);
        return new InternalMediaView(dto.id(), dto.purpose(), dto.visibility(), media.getOwnerUserId(),
                dto.originalFilename(), dto.contentType(), dto.sizeBytes(), dto.url(), dto.urlExpiresAt());
    }

    @Transactional
    public void delete(Long userId, String role, String mediaId) {
        MediaObject media = find(mediaId);
        boolean owner = media.getOwnerUserId().equals(userId);
        if (media.getStatus() == MediaStatus.DELETED || (!owner && !StaffRoles.isStaff(role))) {
            throw new NoSuchElementException("File not found");
        }
        storage.delete(media.getBucket(), media.getObjectKey());
        media.setStatus(MediaStatus.DELETED);
        media.setDeletedAt(LocalDateTime.now(clock));
        repository.save(media);
        audit(userId, AuditAction.DELETE, media, null);
    }

    /**
     * Removes slots that were issued and never completed — an abandoned form, a closed tab, an
     * upload that failed verification on the client side. Runs per tenant; returns how many went.
     */
    @Transactional
    public int sweepAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(props.pendingTtl());
        List<MediaObject> stale = repository.findTop500ByStatusAndCreatedAtBefore(MediaStatus.PENDING, cutoff);
        for (MediaObject media : stale) {
            try {
                storage.delete(media.getBucket(), media.getObjectKey());
            } catch (RuntimeException e) {
                // Most never uploaded anything, so there is often nothing to delete.
                log.debug("No object to remove for abandoned upload {}", media.getId());
            }
            media.setStatus(MediaStatus.DELETED);
            media.setDeletedAt(LocalDateTime.now(clock));
        }
        repository.saveAll(stale);
        return stale.size();
    }

    MediaDTO toDto(MediaObject media) {
        String url = null;
        Instant expires = null;
        if (media.getStatus() == MediaStatus.READY) {
            if (media.getVisibility() == Visibility.PUBLIC) {
                url = storage.publicUrl(media.getBucket(), media.getObjectKey());
            } else {
                url = storage.presignGet(media.getBucket(), media.getObjectKey(),
                        props.downloadUrlTtl(), media.getOriginalFilename());
                expires = clock.instant().plus(props.downloadUrlTtl());
            }
        }
        return new MediaDTO(media.getId(), media.getPurpose().name(), media.getVisibility().name(),
                media.getStatus().name(), media.getOriginalFilename(), media.getContentType(),
                media.getSizeBytes(), url, expires, media.getCreatedAt());
    }

    private void reject(MediaObject media, String reason) {
        try {
            storage.delete(media.getBucket(), media.getObjectKey());
        } catch (RuntimeException e) {
            log.warn("Could not delete rejected upload {}", media.getId(), e);
        }
        media.setStatus(MediaStatus.REJECTED);
        media.setRejectionReason(reason);
        repository.save(media);
        log.warn("Rejected upload {}: {}", media.getId(), reason);
    }

    private MediaObject find(String mediaId) {
        return repository.findById(mediaId).orElseThrow(() -> new NoSuchElementException("File not found"));
    }

    private MediaObject findReady(String mediaId) {
        MediaObject media = find(mediaId);
        if (media.getStatus() != MediaStatus.READY) {
            throw new NoSuchElementException("File not found");
        }
        return media;
    }

    private String bucketFor(Visibility visibility) {
        return visibility == Visibility.PUBLIC ? props.publicBucket() : props.privateBucket();
    }

    private static void requireUser(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Sign in to upload files");
        }
    }

    /**
     * Keeps a name that is safe to echo back in a Content-Disposition header: no path, no control
     * characters, no quotes. It is only ever shown, never used to build a key.
     */
    static String sanitizeFilename(String filename) {
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) name = "file";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private void audit(Long actorId, AuditAction action, MediaObject media, String detail) {
        try {
            auditPublisher.publish(AuditEventMessage.builder()
                    .sourceService(SOURCE)
                    .actorId(actorId)
                    .action(action)
                    .entityType(ENTITY)
                    .entityId(media.getId())
                    .afterState(detail)
                    .build());
        } catch (RuntimeException e) {
            // The audit pipeline being down must not make every upload fail.
            log.error("Could not publish audit event for media {}", media.getId(), e);
        }
    }
}
