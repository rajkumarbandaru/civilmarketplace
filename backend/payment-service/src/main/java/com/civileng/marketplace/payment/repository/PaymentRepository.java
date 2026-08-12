package com.civileng.marketplace.payment.repository;

import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentCode(String paymentCode);

    /**
     * A booking can now carry several payments — one per escrow milestone hold — so anything
     * that wants "the booking's payment" must ask for the most recent one rather than assuming
     * uniqueness, or Spring Data throws IncorrectResultSizeDataAccessException.
     */
    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(Long bookingId);

    Optional<Payment> findFirstByBookingIdAndPaymentStatusOrderByCreatedAtDesc(
            Long bookingId, PaymentStatus status);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByPaymentStatus(PaymentStatus status);

    List<Payment> findByBookingIdAndPaymentStatus(Long bookingId, PaymentStatus status);

    // Admin: date range queries
    List<Payment> findByCreatedAtBetweenAndPaymentStatus(
            LocalDateTime start, LocalDateTime end, PaymentStatus status);

    List<Payment> findByCreatedAtAfterAndPaymentStatus(
            LocalDateTime after, PaymentStatus status);

    // Admin: count queries
    long countByCreatedAtAfterAndPaymentStatus(
            LocalDateTime after, PaymentStatus status);
}
