package com.civileng.marketplace.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "escrow_holds", indexes = {
        @Index(name = "idx_escrow_booking", columnList = "booking_id"),
        @Index(name = "idx_escrow_milestone", columnList = "milestone_id"),
        @Index(name = "idx_escrow_project", columnList = "project_id"),
        @Index(name = "idx_escrow_payee", columnList = "payee_id"),
        @Index(name = "idx_escrow_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "escrow_code", nullable = false, unique = true, length = 30)
    private String escrowCode;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "payee_id", nullable = false)
    private Long payeeId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "commission_amount", precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "net_amount", precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.PENDING_FUNDING;

    @Column(name = "auto_release_at")
    private LocalDateTime autoReleaseAt;

    @Column(name = "funded_at")
    private LocalDateTime fundedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
