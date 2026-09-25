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

/**
 * A supplier's prices: its standard catalogue ({@code buyerOrgId} null), or a contract with one
 * buyer carrying payment terms and a credit limit.
 */
@Entity
@Table(name = "price_lists")
@Getter
@Setter
@NoArgsConstructor
public class PriceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_org_id", nullable = false)
    private Long supplierOrgId;

    @Column(name = "buyer_org_id")
    private Long buyerOrgId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PriceListStatus status;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "priceList", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PriceListItem> items = new ArrayList<>();

    public void addItem(PriceListItem item) {
        item.setPriceList(this);
        items.add(item);
    }

    public boolean isContract() {
        return buyerOrgId != null;
    }

    /** In force on the given day: active and within its dates. */
    public boolean inForce(LocalDate day) {
        return status == PriceListStatus.ACTIVE
                && (validFrom == null || !day.isBefore(validFrom))
                && (validUntil == null || !day.isAfter(validUntil));
    }
}
