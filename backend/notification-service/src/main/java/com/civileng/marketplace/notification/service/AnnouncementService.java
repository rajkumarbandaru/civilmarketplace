package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.notification.client.AuthServiceClient;
import com.civileng.marketplace.notification.dto.AnnouncementDto.CreateAnnouncementRequest;
import com.civileng.marketplace.notification.model.Announcement;
import com.civileng.marketplace.notification.model.AnnouncementStatus;
import com.civileng.marketplace.notification.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Admin broadcast — SRS ENT·04. One-click, in-app fan-out to every user in the target audience,
 * built on top of the existing {@code Notification} row rather than a new delivery mechanism:
 * one row per recipient, same as any other notification type this service already creates.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnnouncementService {

    private static final String SOURCE = "notification-service";
    private static final String ENTITY = "Announcement";
    /** Every user page is fetched at this size; bounds how many round trips one role can take. */
    private static final int PAGE_SIZE = 200;
    /** Safety bound on pages per role query — 100 * PAGE_SIZE = 20,000 users per role, per
     * publish. A platform that outgrows this needs async/batched fan-out (see module notes),
     * not a bigger constant here. */
    private static final int MAX_PAGES_PER_ROLE = 100;

    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;
    private final AuthServiceClient authServiceClient;
    private final AuditPublisher auditPublisher;

    /**
     * Creates an announcement and either sends it now or leaves it waiting for its appointed time.
     *
     * A null {@code scheduledAt} — or one already past, which is what a slightly fast browser
     * clock produces when the operator means "now" — sends immediately.
     */
    @Transactional
    public Announcement publish(Long actorId, String actorRole, CreateAnnouncementRequest request) {
        List<String> roles = request.getTargetRoles();
        String targetRoles = String.join(",", roles);
        Instant scheduledAt = request.getScheduledAt();
        boolean later = scheduledAt != null && scheduledAt.isAfter(Instant.now());

        Announcement saved = announcementRepository.save(Announcement.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .targetRoles(targetRoles)
                .createdBy(actorId)
                .status(later ? AnnouncementStatus.SCHEDULED : AnnouncementStatus.SENT)
                .scheduledAt(later ? scheduledAt : null)
                .build());

        if (later) {
            log.info("Announcement {} scheduled by {} for {} (roles: {})",
                    saved.getId(), actorId, scheduledAt, targetRoles);
        } else {
            // The audience is resolved here rather than at save time so that both paths do it at
            // the moment of sending: a scheduled announcement must reach whoever holds the role
            // when it goes out, not whoever held it when it was written.
            fanOut(saved);
        }

        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(AuditAction.CREATE)
                .entityType(ENTITY)
                .entityId(String.valueOf(saved.getId()))
                .afterState("title=" + saved.getTitle() + ",targetRoles=" + targetRoles
                        + ",status=" + saved.getStatus()
                        + (later ? ",scheduledAt=" + scheduledAt : ""))
                .recordCount(saved.getRecipientCount())
                .build());

        return saved;
    }

    /** Ids of every announcement whose scheduled time has passed and which has not gone out. */
    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return announcementRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        AnnouncementStatus.SCHEDULED, Instant.now())
                .stream()
                .map(Announcement::getId)
                .toList();
    }

    /**
     * Sends one due announcement, if this instance is the one that claims it.
     *
     * Claim and fan-out share a transaction so the row stays locked until the notifications are
     * committed: a second instance's claim blocks on that lock rather than racing past it, and
     * once released finds the row out of SCHEDULED and gives up. A crash mid-send rolls both back
     * and leaves the announcement SCHEDULED, so the next minute retries it.
     */
    @Transactional
    public void release(Long id) {
        if (announcementRepository.claimForSending(id) == 0) {
            log.debug("Announcement {} already claimed by another instance", id);
            return;
        }
        announcementRepository.findById(id).ifPresent(this::fanOut);
    }

    /** Calls off a scheduled announcement. Anything already sent is past recalling. */
    @Transactional
    public Announcement cancel(Long actorId, String actorRole, Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Announcement not found: " + id));
        if (announcement.getStatus() != AnnouncementStatus.SCHEDULED) {
            throw new IllegalArgumentException(
                    "Only a scheduled announcement can be cancelled; this one is "
                            + announcement.getStatus().name().toLowerCase());
        }
        announcement.setStatus(AnnouncementStatus.CANCELLED);
        Announcement saved = announcementRepository.save(announcement);

        log.info("Announcement {} cancelled by {}", id, actorId);
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(AuditAction.UPDATE)
                .entityType(ENTITY)
                .entityId(String.valueOf(id))
                .afterState("status=CANCELLED")
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Announcement> listHistory(Pageable pageable) {
        return announcementRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Resolves the audience and writes one in-app notification per recipient, then marks the
     * announcement sent. Shared by the immediate and the scheduled path so both deliver
     * identically.
     */
    private void fanOut(Announcement announcement) {
        Set<Long> recipients = resolveRecipients(
                List.of(announcement.getTargetRoles().split(",")));

        for (Long userId : recipients) {
            notificationService.createNotification(
                    userId, "ANNOUNCEMENT", announcement.getTitle(), announcement.getBody(),
                    "IN_APP", "ANNOUNCEMENT", announcement.getId(), null);
        }

        announcement.setRecipientCount(recipients.size());
        announcement.setStatus(AnnouncementStatus.SENT);
        announcement.setSentAt(Instant.now());
        announcementRepository.save(announcement);

        log.info("Announcement {} sent to {} recipients (roles: {})",
                announcement.getId(), recipients.size(), announcement.getTargetRoles());
    }

    /**
     * "*" means every ACTIVE user, fetched with no role filter; anything else is one query per
     * role. A user matching more than one requested role (impossible today — one role per user —
     * but the set guards it anyway) is only notified once.
     */
    private Set<Long> resolveRecipients(List<String> roles) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (roles.contains("*")) {
            fetchActiveUserIds(null, recipients);
            return recipients;
        }
        for (String role : roles) {
            fetchActiveUserIds(role, recipients);
        }
        return recipients;
    }

    @SuppressWarnings("unchecked")
    private void fetchActiveUserIds(String role, Set<Long> into) {
        int page = 0;
        int totalPages = 1;
        while (page < totalPages && page < MAX_PAGES_PER_ROLE) {
            Map<String, Object> response = authServiceClient.getUsers(page, PAGE_SIZE, role, "ACTIVE");
            List<Map<String, Object>> users = (List<Map<String, Object>>) response.getOrDefault("data", List.of());
            for (Map<String, Object> user : users) {
                into.add(((Number) user.get("id")).longValue());
            }
            totalPages = ((Number) response.getOrDefault("totalPages", 1)).intValue();
            page++;
        }
        if (page >= MAX_PAGES_PER_ROLE && page < totalPages) {
            log.warn("Announcement audience for role {} truncated at {} pages ({} users) — "
                    + "raise MAX_PAGES_PER_ROLE if this platform's user base has genuinely grown "
                    + "this large.", role == null ? "*" : role, MAX_PAGES_PER_ROLE, into.size());
        }
    }
}
