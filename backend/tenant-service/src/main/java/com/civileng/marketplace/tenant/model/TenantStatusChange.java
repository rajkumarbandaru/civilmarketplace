package com.civileng.marketplace.tenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One lifecycle transition: who moved a tenant from what to what, and why. */
@Entity
@Table(name = "tenant_status_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 31)
    private String tenantKey;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(length = 64)
    private String actor;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime changedAt;
}
