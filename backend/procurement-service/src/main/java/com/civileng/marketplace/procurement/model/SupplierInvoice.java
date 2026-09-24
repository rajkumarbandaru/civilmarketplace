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

/** A supplier's tax invoice against an order, with the three-way match's verdict on it. */
@Entity
@Table(name = "supplier_invoices")
@Getter
@Setter
@NoArgsConstructor
public class SupplierInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    @Column(name = "supplier_org_id", nullable = false)
    private Long supplierOrgId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** One reason per line, newline-separated; empty when matched. */
    @Column(name = "match_issues", columnDefinition = "TEXT")
    private String matchIssues;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplierInvoiceLine> lines = new ArrayList<>();

    public void addLine(SupplierInvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }

    /** Counts against what may still be invoiced: everything but a rejected invoice. */
    public boolean isLive() {
        return status != InvoiceStatus.REJECTED;
    }
}
