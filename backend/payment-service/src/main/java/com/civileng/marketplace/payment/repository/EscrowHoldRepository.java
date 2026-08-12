package com.civileng.marketplace.payment.repository;

import com.civileng.marketplace.payment.model.EscrowHold;
import com.civileng.marketplace.payment.model.EscrowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EscrowHoldRepository extends JpaRepository<EscrowHold, Long> {

    Optional<EscrowHold> findByEscrowCode(String escrowCode);

    /**
     * A list, not an Optional: historical rows can share a funding payment, and a non-unique
     * result must not blow up the funding path with a 500.
     */
    List<EscrowHold> findByPaymentId(Long paymentId);

    List<EscrowHold> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    List<EscrowHold> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<EscrowHold> findByMilestoneIdAndStatusIn(Long milestoneId, List<EscrowStatus> statuses);

    Page<EscrowHold> findByPayerIdOrderByCreatedAtDesc(Long payerId, Pageable pageable);

    Page<EscrowHold> findByPayeeIdOrderByCreatedAtDesc(Long payeeId, Pageable pageable);

    Page<EscrowHold> findByStatusOrderByCreatedAtDesc(EscrowStatus status, Pageable pageable);

    /** Auto-release sweep: HELD, past its timer. DISPUTED is excluded by the status filter. */
    List<EscrowHold> findByStatusAndAutoReleaseAtLessThanEqual(EscrowStatus status, LocalDateTime cutoff);
}
