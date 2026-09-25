package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.model.OrgMember;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Tells the people of an organization that something needs them: an RFQ to quote, an order to
 * approve or supply, an invoice to decide on or that was paid. notification-service turns each
 * {@code procurement.events} message into an in-app notification and an email per recipient.
 *
 * <p>Sent after commit, so nobody is told about an order whose transaction then rolled back.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Notifier {

    public static final String TOPIC = "procurement.events";

    private final OrgMemberRepository members;
    private final KafkaTemplate<String, Object> kafka;

    public record Recipient(Long userId, String email) { }

    public record Notice(String type, String title, String message, String referenceType, Long referenceId,
                         String link, List<Recipient> recipients) { }

    public static final Predicate<OrgMember> EVERYONE = m -> true;
    public static final Predicate<OrgMember> APPROVERS = OrgMember::canApprove;

    /** Everyone in {@code orgId} matching {@code who}, except the person who caused it. */
    public void toOrg(Long orgId, Predicate<OrgMember> who, Long exceptUserId, String type, String title,
                      String message, String referenceType, Long referenceId, String link) {
        List<Recipient> recipients = members.findByOrganizationIdOrderByIdAsc(orgId).stream()
                .filter(who)
                .filter(m -> exceptUserId == null || !Objects.equals(m.getUserId(), exceptUserId))
                .map(m -> new Recipient(m.getUserId(), m.getEmail()))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        Notice notice = new Notice(type, title, message, referenceType, referenceId, link, recipients);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(notice);
                }
            });
        } else {
            send(notice);
        }
    }

    private void send(Notice notice) {
        try {
            kafka.send(TOPIC, notice.referenceType() + ":" + notice.referenceId(), notice);
        } catch (RuntimeException e) {
            // A notification failing to send must not undo the trade it describes.
            log.error("Could not publish {} for {} {}", notice.type(), notice.referenceType(), notice.referenceId(), e);
        }
    }
}
