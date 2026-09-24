package com.civileng.marketplace.tenant.entitlement;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** What a tenant bought: a plan version, add-ons, and where the subscription stands. */
@Entity
@Table(name = "tenant_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscription {

    public enum Status { TRIALING, ACTIVE, PAST_DUE, SUSPENDED, CANCELED }

    @Id
    @Column(name = "tenant_key", length = 31)
    private String tenantKey;

    @Column(name = "plan_key", nullable = false, length = 40)
    private String planKey;

    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Comma-separated add-on keys. */
    @Column(name = "add_ons", nullable = false, length = 500)
    private String addOns;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
