package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.payment.dto.CreateEscrowRequest;
import com.civileng.marketplace.payment.model.*;
import com.civileng.marketplace.payment.repository.EscrowHoldRepository;
import com.civileng.marketplace.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Milestone escrow — SRS CP·06 FR-06/FR-09.
 *
 * <p>The invariant worth protecting: <b>a hold only becomes HELD because its linked payment
 * completed at the PSP</b>. There is no endpoint by which a payer declares their own money
 * received, so the platform cannot be talked into releasing funds that never arrived.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EscrowService {

    private static final String SOURCE = "payment-service";
    private static final String ENTITY = "EscrowHold";

    private final EscrowHoldRepository escrowRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditPublisher auditPublisher;

    /** Category-configurable by Super Admin per FR-03; a single platform rate until that exists. */
    @Value("${escrow.commission-rate:5.00}")
    private BigDecimal commissionRate;

    @Value("${escrow.auto-release-days:7}")
    private int autoReleaseDays;

    // ------------------------------------------------------------------ creation and funding

    @Transactional
    public EscrowHold createHold(Long payerId, CreateEscrowRequest request) {
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Escrow amount must be positive");
        }
        if (payerId.equals(request.getPayeeId())) {
            throw new IllegalArgumentException("Payer and payee cannot be the same user");
        }
        if (request.getMilestoneId() != null) {
            List<EscrowHold> open = escrowRepository.findByMilestoneIdAndStatusIn(
                    request.getMilestoneId(),
                    List.of(EscrowStatus.PENDING_FUNDING, EscrowStatus.HELD, EscrowStatus.DISPUTED));
            if (!open.isEmpty()) {
                throw new IllegalArgumentException(
                        "This milestone already has an open escrow hold ("
                                + open.get(0).getEscrowCode() + ")");
            }
        }

        EscrowHold hold = EscrowHold.builder()
                .escrowCode(generateEscrowCode())
                .projectId(request.getProjectId())
                .milestoneId(request.getMilestoneId())
                .bookingId(request.getBookingId())
                .payerId(payerId)
                .payeeId(request.getPayeeId())
                .amount(request.getAmount())
                .commissionRate(commissionRate)
                .status(EscrowStatus.PENDING_FUNDING)
                .notes(request.getNotes())
                .build();

        // Funding runs through the existing PSP path, so escrow inherits the same webhook
        // idempotency and signature verification as a direct payment.
        Payment payment = paymentService.createEscrowFundingOrder(
                request.getBookingId(), payerId, request.getAmount());
        hold.setPaymentId(payment.getId());

        EscrowHold saved = escrowRepository.save(hold);
        log.info("Escrow {} created for booking {} milestone {} amount {}",
                saved.getEscrowCode(), saved.getBookingId(), saved.getMilestoneId(),
                saved.getAmount());
        audit(AuditAction.CREATE, payerId, null, saved, null,
                "status=PENDING_FUNDING,amount=" + saved.getAmount(), null);
        return saved;
    }

    /**
     * Called when a payment completes (PSP verify or webhook). Idempotent: a duplicate webhook
     * delivery is expected rather than exceptional, per CP·06's NFR.
     */
    @org.springframework.context.event.EventListener
    @Transactional
    public void onPaymentCompleted(
            com.civileng.marketplace.payment.event.PaymentCompletedEvent event) {
        onPaymentCompleted(event.paymentId());
    }

    @Transactional
    public void onPaymentCompleted(Long paymentId) {
        escrowRepository.findByPaymentId(paymentId).stream()
                .filter(hold -> hold.getStatus() == EscrowStatus.PENDING_FUNDING)
                .forEach(this::markHeld);
    }

    private EscrowHold markHeld(EscrowHold hold) {
        hold.setStatus(EscrowStatus.HELD);
        hold.setFundedAt(LocalDateTime.now());
        hold.setAutoReleaseAt(LocalDateTime.now().plusDays(autoReleaseDays));
        EscrowHold saved = escrowRepository.save(hold);
        log.info("Escrow {} funded and held, auto-release at {}",
                saved.getEscrowCode(), saved.getAutoReleaseAt());
        kafkaTemplate.send("escrow.held", Map.of(
                "escrowId", saved.getId(),
                "bookingId", saved.getBookingId(),
                "amount", saved.getAmount()));
        audit(AuditAction.UPDATE, null, null, saved, "status=PENDING_FUNDING",
                "status=HELD", "Funding payment completed");
        return saved;
    }

    /**
     * Reconciliation for the case where the completion callback was missed — reads the linked
     * payment and promotes the hold if the PSP says it is paid. Safe to call on any read path.
     */
    @Transactional
    public EscrowHold refreshFunding(EscrowHold hold) {
        if (hold.getStatus() != EscrowStatus.PENDING_FUNDING || hold.getPaymentId() == null) {
            return hold;
        }
        return paymentRepository.findById(hold.getPaymentId())
                .filter(p -> p.getPaymentStatus() == PaymentStatus.COMPLETED)
                .map(p -> markHeld(hold))
                .orElse(hold);
    }

    // ------------------------------------------------------------------ release and refund

    /**
     * FR-06's explicit payer confirmation. Commission is computed and frozen onto the row here,
     * so the arithmetic stays reproducible even if the platform rate changes later.
     */
    @Transactional
    public EscrowHold release(Long escrowId, Long actorId, boolean actorIsAdmin) {
        EscrowHold hold = refreshFunding(require(escrowId));

        if (!actorIsAdmin && !hold.getPayerId().equals(actorId)) {
            throw new com.civileng.marketplace.payment.exception.AccessDeniedException(
                    "Only the payer may release this escrow");
        }
        return doRelease(hold, actorId, actorIsAdmin ? "Released by admin" : "Released by payer");
    }

    private EscrowHold doRelease(EscrowHold hold, Long actorId, String reason) {
        if (hold.getStatus() == EscrowStatus.DISPUTED) {
            throw new IllegalArgumentException(
                    "This escrow is under dispute and cannot be released until it is resolved");
        }
        if (hold.getStatus() != EscrowStatus.HELD) {
            throw new IllegalArgumentException(
                    "Only a funded (HELD) escrow can be released — this one is " + hold.getStatus());
        }

        BigDecimal commission = hold.getAmount()
                .multiply(hold.getCommissionRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = hold.getAmount().subtract(commission);

        hold.setCommissionAmount(commission);
        hold.setNetAmount(net);
        hold.setStatus(EscrowStatus.RELEASED);
        hold.setReleasedAt(LocalDateTime.now());
        hold.setReleasedBy(actorId);
        EscrowHold saved = escrowRepository.save(hold);

        walletService.credit(saved.getPayeeId(), net, WalletTransactionType.ESCROW_RELEASE,
                "Escrow " + saved.getEscrowCode() + " released (commission " + commission + ")",
                "ESCROW", saved.getId());

        log.info("Escrow {} released: {} to payee {} (commission {})",
                saved.getEscrowCode(), net, saved.getPayeeId(), commission);
        kafkaTemplate.send("escrow.released", Map.of(
                "escrowId", saved.getId(),
                "bookingId", saved.getBookingId(),
                "payeeId", saved.getPayeeId(),
                "netAmount", net));
        audit(AuditAction.UPDATE, actorId, null, saved, "status=HELD",
                "status=RELEASED,net=" + net + ",commission=" + commission, reason);
        return saved;
    }

    /** Payer or admin pulls the money back before release. No commission is taken on a refund. */
    @Transactional
    public EscrowHold refund(Long escrowId, Long actorId, boolean actorIsAdmin, String reason) {
        EscrowHold hold = refreshFunding(require(escrowId));
        if (!actorIsAdmin && !hold.getPayerId().equals(actorId)) {
            throw new com.civileng.marketplace.payment.exception.AccessDeniedException(
                    "Only the payer or an admin may refund this escrow");
        }
        if (hold.getStatus() == EscrowStatus.DISPUTED && !actorIsAdmin) {
            throw new IllegalArgumentException(
                    "A disputed escrow can only be refunded by an admin resolving the dispute");
        }
        if (hold.getStatus().isTerminal()) {
            throw new IllegalArgumentException("This escrow is already " + hold.getStatus());
        }

        EscrowStatus previous = hold.getStatus();
        hold.setStatus(previous == EscrowStatus.PENDING_FUNDING
                ? EscrowStatus.CANCELLED : EscrowStatus.REFUNDED);
        hold.setRefundedAt(LocalDateTime.now());
        hold.setNotes(reason);
        EscrowHold saved = escrowRepository.save(hold);

        // An unfunded hold has nothing at the PSP to reverse; a funded one does.
        if (previous == EscrowStatus.HELD && saved.getPaymentId() != null) {
            try {
                paymentService.processRefund(saved.getPaymentId(), saved.getAmount(), reason);
            } catch (RuntimeException e) {
                // The hold's own state is already correct and audited; a PSP refund failure is a
                // recoverable operational problem, not a reason to leave escrow inconsistent.
                log.error("Escrow {} marked refunded but PSP refund failed: {}",
                        saved.getEscrowCode(), e.getMessage());
            }
        }

        log.info("Escrow {} {} by {}", saved.getEscrowCode(), saved.getStatus(), actorId);
        audit(AuditAction.UPDATE, actorId, null, saved, "status=" + previous,
                "status=" + saved.getStatus(), reason);
        return saved;
    }

    // ------------------------------------------------------------------ disputes

    /** Either party may raise a dispute; it freezes the hold against release (FR-09). */
    @Transactional
    public EscrowHold dispute(Long escrowId, Long actorId, String reason) {
        EscrowHold hold = refreshFunding(require(escrowId));
        if (!hold.getPayerId().equals(actorId) && !hold.getPayeeId().equals(actorId)) {
            throw new com.civileng.marketplace.payment.exception.AccessDeniedException(
                    "Only a party to this escrow may dispute it");
        }
        if (hold.getStatus() != EscrowStatus.HELD) {
            throw new IllegalArgumentException(
                    "Only a funded (HELD) escrow can be disputed — this one is " + hold.getStatus());
        }

        hold.setStatus(EscrowStatus.DISPUTED);
        hold.setDisputeReason(reason);
        hold.setDisputedAt(LocalDateTime.now());
        EscrowHold saved = escrowRepository.save(hold);
        log.info("Escrow {} disputed by {}: {}", saved.getEscrowCode(), actorId, reason);
        kafkaTemplate.send("escrow.disputed", Map.of(
                "escrowId", saved.getId(),
                "bookingId", saved.getBookingId(),
                "raisedBy", actorId));
        audit(AuditAction.UPDATE, actorId, null, saved, "status=HELD", "status=DISPUTED", reason);
        return saved;
    }

    /**
     * Admin decides a dispute. RELEASE pays the payee, REFUND returns the payer, HOLD puts it back
     * to HELD for the parties to settle themselves. Full dispute resolution is TR·03 and out of
     * migration scope — this is the minimum that keeps disputed money from being stuck forever.
     */
    @Transactional
    public EscrowHold resolveDispute(Long escrowId, Long adminId, String outcome, String reason) {
        EscrowHold hold = require(escrowId);
        if (hold.getStatus() != EscrowStatus.DISPUTED) {
            throw new IllegalArgumentException("This escrow is not disputed");
        }
        String decision = outcome == null ? "" : outcome.trim().toUpperCase();
        switch (decision) {
            case "RELEASE" -> {
                hold.setStatus(EscrowStatus.HELD);
                escrowRepository.save(hold);
                return doRelease(hold, adminId, "Dispute resolved in payee's favour: " + reason);
            }
            case "REFUND" -> {
                hold.setStatus(EscrowStatus.HELD);
                escrowRepository.save(hold);
                return refund(escrowId, adminId, true, "Dispute resolved in payer's favour: " + reason);
            }
            case "HOLD" -> {
                hold.setStatus(EscrowStatus.HELD);
                hold.setDisputeReason(null);
                hold.setDisputedAt(null);
                EscrowHold saved = escrowRepository.save(hold);
                audit(AuditAction.UPDATE, adminId, null, saved, "status=DISPUTED",
                        "status=HELD", reason);
                return saved;
            }
            default -> throw new IllegalArgumentException(
                    "Outcome must be RELEASE, REFUND or HOLD");
        }
    }

    // ------------------------------------------------------------------ auto-release

    /**
     * FR-06's auto-release timer. Each hold is released in its own transaction so one failure
     * does not abort the sweep, and DISPUTED holds are excluded by the query, not by a filter
     * that could be forgotten.
     */
    @Transactional(readOnly = true)
    public List<EscrowHold> findDueForAutoRelease() {
        return escrowRepository.findByStatusAndAutoReleaseAtLessThanEqual(
                EscrowStatus.HELD, LocalDateTime.now());
    }

    @Transactional
    public void autoRelease(Long escrowId) {
        EscrowHold hold = require(escrowId);
        if (hold.getStatus() != EscrowStatus.HELD) return;
        doRelease(hold, null, "Auto-released after " + autoReleaseDays + " days without payer action");
    }

    // ------------------------------------------------------------------ reads

    @Transactional
    public EscrowHold get(Long escrowId, Long actorId, boolean actorIsAdmin) {
        EscrowHold hold = refreshFunding(require(escrowId));
        if (!actorIsAdmin && !hold.getPayerId().equals(actorId)
                && !hold.getPayeeId().equals(actorId)) {
            throw new com.civileng.marketplace.payment.exception.AccessDeniedException(
                    "You are not a party to this escrow");
        }
        return hold;
    }

    @Transactional(readOnly = true)
    public List<EscrowHold> getForBooking(Long bookingId) {
        return escrowRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    /** Feeds project-service's budget rollup — no per-user filtering, it is service-to-service. */
    @Transactional(readOnly = true)
    public List<EscrowHold> getForProject(Long projectId) {
        return escrowRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Page<EscrowHold> listAsPayer(Long payerId, Pageable pageable) {
        return escrowRepository.findByPayerIdOrderByCreatedAtDesc(payerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<EscrowHold> listAsPayee(Long payeeId, Pageable pageable) {
        return escrowRepository.findByPayeeIdOrderByCreatedAtDesc(payeeId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<EscrowHold> listAll(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return escrowRepository.findAll(pageable);
        }
        try {
            return escrowRepository.findByStatusOrderByCreatedAtDesc(
                    EscrowStatus.valueOf(status.trim().toUpperCase()), pageable);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown escrow status: " + status);
        }
    }

    // ------------------------------------------------------------------ helpers

    private EscrowHold require(Long escrowId) {
        return escrowRepository.findById(escrowId)
                .orElseThrow(() -> new IllegalArgumentException("Escrow hold not found"));
    }

    private String generateEscrowCode() {
        return "ESC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + (1000 + new Random().nextInt(9000));
    }

    private void audit(AuditAction action, Long actorId, String actorRole, EscrowHold hold,
                       String before, String after, String reason) {
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .entityType(ENTITY)
                .entityId(String.valueOf(hold.getId()))
                .subjectUserId(hold.getPayeeId())
                .beforeState(before)
                .afterState(after)
                .reason(reason)
                .build());
    }
}
