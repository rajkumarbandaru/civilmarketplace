package com.civileng.marketplace.auditservice.service;

import com.civileng.marketplace.auditservice.model.AuditEvent;
import com.civileng.marketplace.auditservice.model.ErasureRequest;
import com.civileng.marketplace.auditservice.repository.AuditEventRepository;
import com.civileng.marketplace.auditservice.repository.ErasureRequestRepository;
import com.civileng.marketplace.auditservice.util.AuditHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;
    private final ErasureRequestRepository erasureRequestRepository;

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(Long actorId, Long subjectUserId, String entityType,
                                   String action, Instant from, Instant to, Pageable pageable) {
        return auditEventRepository.search(actorId, subjectUserId, entityType, action,
                from, to, pageable);
    }

    /** Right-to-access export: every audit record concerning one data subject. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportForUser(Long userId) {
        List<AuditEvent> events = auditEventRepository.findBySubjectUserIdOrderByIdAsc(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("userId", userId);
        export.put("generatedAt", Instant.now().toString());
        export.put("eventCount", events.size());
        export.put("events", events);
        return export;
    }

    @Transactional
    public ErasureRequest requestErasure(Long userId, Long requestedBy, String reason) {
        ErasureRequest request = erasureRequestRepository.save(ErasureRequest.builder()
                .userId(userId)
                .requestedBy(requestedBy)
                .reason(reason)
                .status("RECEIVED")
                .build());
        log.info("Erasure request {} recorded for user {}", request.getId(), userId);
        return request;
    }

    @Transactional
    public ErasureRequest updateErasureStatus(Long id, String status, Long handledBy, String note) {
        ErasureRequest request = erasureRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Erasure request not found"));
        request.setStatus(status);
        request.setHandledBy(handledBy);
        request.setHandledAt(LocalDateTime.now());
        request.setResolutionNote(note);
        return erasureRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ErasureRequest> erasureRequestsFor(Long userId) {
        return erasureRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Verifies the audit log two ways and reports the first failure of either:
     * <ol>
     *   <li><b>Content integrity</b> — recompute each row's hash from its currently-stored
     *       fields and compare to the stored {@code event_hash}. Catches a row whose content
     *       was edited in place (e.g. a direct UPDATE) without also rewriting its hash.</li>
     *   <li><b>Chain integrity</b> — each row's {@code previous_hash} must equal the preceding
     *       row's hash. Catches deletion or reordering, which content verification alone
     *       cannot: a deleted row's neighbours still each hash correctly on their own.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyIntegrity() {
        List<AuditEvent> events = auditEventRepository.findTop2000ByOrderByIdAsc();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventsChecked", events.size());

        String expectedPrevious = null;
        for (AuditEvent e : events) {
            String recomputed = AuditHasher.computeHash(e, e.getPreviousHash());
            if (!recomputed.equals(e.getEventHash())) {
                result.put("intact", false);
                result.put("brokenAtEventId", e.getId());
                result.put("detail", "stored event_hash does not match a hash recomputed from "
                        + "this row's current content — the row was edited after being recorded");
                return result;
            }

            String actualPrevious = e.getPreviousHash();
            boolean linked = (expectedPrevious == null && actualPrevious == null)
                    || (expectedPrevious != null && expectedPrevious.equals(actualPrevious));
            if (!linked) {
                result.put("intact", false);
                result.put("brokenAtEventId", e.getId());
                result.put("detail", "previous_hash does not match the preceding event's hash — "
                        + "a record was deleted or the log was reordered");
                return result;
            }
            expectedPrevious = e.getEventHash();
        }
        result.put("intact", true);
        return result;
    }
}
