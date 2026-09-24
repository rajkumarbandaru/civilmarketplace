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

/** A company acting in trade within one tenant: a buyer, a supplier, a contractor, or several. */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 15)
    private String gstin;

    @Convert(converter = CapabilitySetConverter.class)
    @Column(nullable = false, length = 200)
    private Set<Capability> capabilities = EnumSet.noneOf(Capability.class);

    @Column(name = "approval_threshold", precision = 15, scale = 2)
    private BigDecimal approvalThreshold;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public boolean can(Capability capability) {
        return capabilities.contains(capability);
    }
}
