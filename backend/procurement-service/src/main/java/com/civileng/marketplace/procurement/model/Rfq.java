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

/** A request for quotation: what a buyer needs, sent to the suppliers it chose. */
@Entity
@Table(name = "rfqs")
@Getter
@Setter
@NoArgsConstructor
public class Rfq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String number;

    @Column(name = "buyer_org_id", nullable = false)
    private Long buyerOrgId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "delivery_site", length = 300)
    private String deliverySite;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(length = 120)
    private String reference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RfqStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<RfqLine> lines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "rfq_invitations", joinColumns = @JoinColumn(name = "rfq_id"))
    @Column(name = "supplier_org_id")
    private Set<Long> invitedSupplierIds = new java.util.LinkedHashSet<>();

    public void addLine(RfqLine line) {
        line.setRfq(this);
        line.setLineNo(lines.size() + 1);
        lines.add(line);
    }
}
