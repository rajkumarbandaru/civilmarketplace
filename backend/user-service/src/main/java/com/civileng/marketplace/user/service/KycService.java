package com.civileng.marketplace.user.service;

import com.civileng.marketplace.user.model.KycDocument;
import com.civileng.marketplace.user.model.UserProfile;
import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.user.repository.KycDocumentRepository;
import com.civileng.marketplace.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KycService {

    private static final String SOURCE = "user-service";
    private static final String ENTITY = "KycDocument";

    private final KycDocumentRepository kycDocumentRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuditPublisher auditPublisher;

    @Transactional
    public KycDocument submitDocument(Long userId, KycDocument document) {
        if (kycDocumentRepository.existsByUserIdAndDocumentTypeAndStatus(
                userId, document.getDocumentType(), KycDocument.KycStatus.PENDING)) {
            throw new IllegalArgumentException(
                    "A " + document.getDocumentType() + " document is already pending review");
        }
        document.setId(null);
        document.setUserId(userId);
        document.setStatus(KycDocument.KycStatus.PENDING);
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        document.setRejectionReason(null);
        KycDocument saved = kycDocumentRepository.save(document);
        log.info("KYC document {} submitted for user {}", saved.getDocumentType(), userId);

        audit(AuditAction.CREATE, userId, null, String.valueOf(saved.getId()), userId,
                null, "status=PENDING,type=" + saved.getDocumentType(), null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<KycDocument> getMyDocuments(Long userId) {
        List<KycDocument> documents = kycDocumentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        audit(AuditAction.READ, userId, null, null, userId, null, null,
                "own documents", documents.size());
        return documents;
    }

    /**
     * Admin review queue. This is a read of other people's identity documents, so it is audited
     * with a record count — that count is what feeds bulk-access anomaly detection.
     */
    @Transactional(readOnly = true)
    public Page<KycDocument> getPendingDocuments(Pageable pageable, Long actorId, String actorRole) {
        Page<KycDocument> page =
                kycDocumentRepository.findByStatus(KycDocument.KycStatus.PENDING, pageable);
        audit(AuditAction.READ, actorId, actorRole, null, null, null, null,
                "pending KYC review queue", page.getNumberOfElements());
        return page;
    }

    @Transactional
    public KycDocument approve(Long documentId, Long reviewerId, String reviewerRole) {
        KycDocument document = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("KYC document not found"));
        String before = "status=" + document.getStatus();
        document.setStatus(KycDocument.KycStatus.APPROVED);
        document.setReviewedBy(reviewerId);
        document.setReviewedAt(LocalDateTime.now());
        document.setRejectionReason(null);
        KycDocument saved = kycDocumentRepository.save(document);

        userProfileRepository.findByUserId(document.getUserId()).ifPresent(profile -> {
            profile.setIsVerified(true);
            userProfileRepository.save(profile);
        });

        log.info("KYC document {} approved by {}", documentId, reviewerId);

        audit(AuditAction.APPROVE, reviewerId, reviewerRole, String.valueOf(documentId),
                document.getUserId(), before, "status=APPROVED", null, null);
        return saved;
    }

    @Transactional
    public KycDocument reject(Long documentId, Long reviewerId, String reviewerRole, String reason) {
        KycDocument document = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("KYC document not found"));
        String before = "status=" + document.getStatus();
        document.setStatus(KycDocument.KycStatus.REJECTED);
        document.setReviewedBy(reviewerId);
        document.setReviewedAt(LocalDateTime.now());
        document.setRejectionReason(reason);
        KycDocument saved = kycDocumentRepository.save(document);
        log.info("KYC document {} rejected by {}: {}", documentId, reviewerId, reason);

        audit(AuditAction.REJECT, reviewerId, reviewerRole, String.valueOf(documentId),
                document.getUserId(), before, "status=REJECTED", reason, null);
        return saved;
    }

    private void audit(AuditAction action, Long actorId, String actorRole, String entityId,
                       Long subjectUserId, String before, String after,
                       String reason, Integer recordCount) {
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .entityType(ENTITY)
                .entityId(entityId)
                .subjectUserId(subjectUserId)
                .beforeState(before)
                .afterState(after)
                .reason(reason)
                .recordCount(recordCount)
                .build());
    }
}
