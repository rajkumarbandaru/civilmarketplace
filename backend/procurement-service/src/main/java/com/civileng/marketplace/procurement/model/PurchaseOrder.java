package com.civileng.marketplace.procurement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** An order issued from an accepted quotation. Its lines are a snapshot: nothing upstream changes them. */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String number;

    @Column(name = "rfq_id", nullable = false)
    private Long rfqId;

    @Column(name = "quotation_id", nullable = false)
    private Long quotationId;

    @Column(name = "buyer_org_id", nullable = false)
    private Long buyerOrgId;

    @Column(name = "supplier_org_id", nullable = false)
    private Long supplierOrgId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** Net N: invoices are due N days after approval. From the contract, if one applied. */
    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "delivery_site", length = 300)
    private String deliverySite;

    @Column(length = 120)
    private String reference;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public void addLine(PurchaseOrderLine line) {
        line.setPurchaseOrder(this);
        line.setLineNo(lines.size() + 1);
        lines.add(line);
    }
}
