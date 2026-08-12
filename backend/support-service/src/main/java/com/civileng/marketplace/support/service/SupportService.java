package com.civileng.marketplace.support.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.support.dto.AssignRequest;
import com.civileng.marketplace.support.dto.CreateTicketRequest;
import com.civileng.marketplace.support.dto.ReplyRequest;
import com.civileng.marketplace.support.dto.StatusChangeRequest;
import com.civileng.marketplace.support.exception.AccessDeniedException;
import com.civileng.marketplace.support.model.SupportTicket;
import com.civileng.marketplace.support.model.TicketMessage;
import com.civileng.marketplace.support.model.TicketPriority;
import com.civileng.marketplace.support.model.TicketStatus;
import com.civileng.marketplace.support.repository.SupportTicketRepository;
import com.civileng.marketplace.support.repository.TicketMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class SupportService {

    private static final String SOURCE = "support-service";
    private static final String ENTITY = "SupportTicket";

    /** From auth-service's roles seed data. */
    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final SupportTicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final AuditPublisher auditPublisher;

    @Transactional
    public SupportTicket createTicket(Long reporterId, CreateTicketRequest request) {
        SupportTicket ticket = SupportTicket.builder()
                .reporterId(reporterId)
                .subject(request.getSubject())
                .description(request.getDescription())
                .category(request.getCategory())
                .priority(parsePriority(request.getPriority()))
                .status(TicketStatus.OPEN)
                .build();

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Ticket {} created by reporter {}", saved.getId(), reporterId);

        audit(AuditAction.CREATE, reporterId, null, saved.getId(), reporterId,
                null, "status=OPEN,subject=" + saved.getSubject(), null);
        return saved;
    }

    @Transactional(readOnly = true)
    public SupportTicket getTicket(Long ticketId, Long actorId, String actorRole) {
        SupportTicket ticket = requireTicket(ticketId);
        requireParty(ticket, actorId, actorRole);
        return ticket;
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> listMine(Long reporterId, String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return ticketRepository.findByReporterIdOrderByCreatedAtDesc(reporterId, pageable);
        }
        return ticketRepository.findByReporterIdAndStatusOrderByCreatedAtDesc(
                reporterId, parseStatus(status), pageable);
    }

    /** Admin queue — all tickets, optionally filtered by status. */
    @Transactional(readOnly = true)
    public Page<SupportTicket> listAll(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return ticketRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return ticketRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);
    }

    @Transactional
    public SupportTicket assignTicket(Long ticketId, Long actorId, String actorRole,
                                      AssignRequest request) {
        requireAdmin(actorRole);
        SupportTicket ticket = requireTicket(ticketId);

        Long previousAssignee = ticket.getAssigneeId();
        ticket.setAssigneeId(request.getAssigneeId());
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Ticket {} assigned to {} by {}", ticketId, request.getAssigneeId(), actorId);

        audit(AuditAction.UPDATE, actorId, actorRole, ticketId, ticket.getReporterId(),
                "assigneeId=" + previousAssignee, "assigneeId=" + request.getAssigneeId(), null);
        return saved;
    }

    /**
     * Only the assignee or an admin may transition status — the reporter reports and replies,
     * but does not self-resolve, mirroring project-service's owner-vs-viewer split.
     */
    @Transactional
    public SupportTicket changeStatus(Long ticketId, Long actorId, String actorRole,
                                      StatusChangeRequest request) {
        SupportTicket ticket = requireTicket(ticketId);
        requireAssigneeOrAdmin(ticket, actorId, actorRole);

        TicketStatus target = parseStatus(request.getStatus());
        TicketStatus current = ticket.getStatus();
        if (current == target) {
            throw new IllegalArgumentException("Ticket is already " + target);
        }
        if (current.isTerminal()) {
            throw new IllegalArgumentException(
                    "A " + current + " ticket cannot transition to " + target);
        }

        ticket.setStatus(target);
        ticket.setClosedAt(target.isTerminal() ? LocalDateTime.now() : null);
        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Ticket {} status {} -> {} by {}", ticketId, current, target, actorId);

        audit(AuditAction.UPDATE, actorId, actorRole, ticketId, ticket.getReporterId(),
                "status=" + current, "status=" + target, request.getReason());
        return saved;
    }

    // --------------------------------------------------------------------- reply thread

    @Transactional
    public TicketMessage addReply(Long ticketId, Long actorId, String actorRole,
                                  ReplyRequest request) {
        SupportTicket ticket = requireTicket(ticketId);
        requireParty(ticket, actorId, actorRole);
        if (ticket.getStatus().isTerminal()) {
            throw new IllegalArgumentException(
                    "A " + ticket.getStatus() + " ticket no longer accepts replies");
        }

        TicketMessage message = messageRepository.save(TicketMessage.builder()
                .ticketId(ticketId)
                .senderId(actorId)
                .body(request.getBody())
                .build());
        log.info("Reply added to ticket {} by {}", ticketId, actorId);
        return message;
    }

    @Transactional(readOnly = true)
    public List<TicketMessage> listReplies(Long ticketId, Long actorId, String actorRole) {
        SupportTicket ticket = requireTicket(ticketId);
        requireParty(ticket, actorId, actorRole);
        return messageRepository.findByTicketIdOrderByIdAsc(ticketId);
    }

    // --------------------------------------------------------------------- helpers

    private SupportTicket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
    }

    /** Reporter, assignee, or any staff role. */
    private void requireParty(SupportTicket ticket, Long actorId, String actorRole) {
        if (ticket.involves(actorId)) return;
        if (actorRole != null && ADMIN_ROLES.contains(actorRole)) return;
        throw new AccessDeniedException("You do not have access to this ticket");
    }

    private void requireAssigneeOrAdmin(SupportTicket ticket, Long actorId, String actorRole) {
        if (actorId != null && actorId.equals(ticket.getAssigneeId())) return;
        if (actorRole != null && ADMIN_ROLES.contains(actorRole)) return;
        throw new AccessDeniedException("Only the assignee or an admin may change ticket status");
    }

    private void requireAdmin(String actorRole) {
        if (actorRole == null || !ADMIN_ROLES.contains(actorRole)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private TicketStatus parseStatus(String value) {
        try {
            return TicketStatus.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown ticket status: " + value);
        }
    }

    private TicketPriority parsePriority(String value) {
        if (value == null || value.isBlank()) return TicketPriority.MEDIUM;
        try {
            return TicketPriority.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown ticket priority: " + value);
        }
    }

    private void audit(AuditAction action, Long actorId, String actorRole, Long entityId,
                       Long subjectUserId, String before, String after, String reason) {
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .entityType(ENTITY)
                .entityId(entityId == null ? null : String.valueOf(entityId))
                .subjectUserId(subjectUserId)
                .beforeState(before)
                .afterState(after)
                .reason(reason)
                .build());
    }
}
