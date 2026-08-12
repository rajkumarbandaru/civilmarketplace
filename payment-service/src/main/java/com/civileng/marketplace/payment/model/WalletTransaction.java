package com.civileng.marketplace.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Append-only ledger line. Every wallet balance change writes one; nothing updates them. */
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_txn_wallet", columnList = "wallet_id"),
        @Index(name = "idx_wallet_txn_type", columnList = "transaction_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private WalletTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "COMPLETED";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
