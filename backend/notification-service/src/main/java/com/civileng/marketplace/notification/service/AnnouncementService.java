package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.notification.client.AuthServiceClient;
import com.civileng.marketplace.notification.dto.AnnouncementDto.CreateAnnouncementRequest;
import com.civileng.marketplace.notification.model.Announcement;
import com.civileng.marketplace.notification.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    @Transactional
    public Announcement publish(Long actorId, String actorRole, CreateAnnouncementRequest request) {
        List<String> roles = request.getTargetRoles();
        String targetRoles = String.join(",", roles);

        Set<Long> recipients = resolveRecipients(roles);

        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .targetRoles(targetRoles)
                .createdBy(actorId)
                .recipientCount(recipients.size())
                .build();
        Announcement saved = announcementRepository.save(announcement);

        for (Long userId : recipients) {
            notificationService.createNotification(
                    userId, "ANNOUNCEMENT", saved.getTitle(), saved.getBody(),
                    "IN_APP", "ANNOUNCEMENT", saved.getId(), null);
        }

        log.info("Announcement {} published by {} to {} recipients (roles: {})",
                saved.getId(), actorId, recipients.size(), targetRoles);

        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(AuditAction.CREATE)
                .entityType(ENTITY)
                .entityId(String.valueOf(saved.getId()))
                .afterState("title=" + saved.getTitle() + ",targetRoles=" + targetRoles)
                .recordCount(recipients.size())
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Announcement> listHistory(Pageable pageable) {
        return announcementRepository.findAllByOrderByCreatedAtDesc(pageable);
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
