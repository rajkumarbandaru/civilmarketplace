package com.civileng.marketplace.tenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** The provisioning saga's persisted progress for one tenant (architecture 03 §5). */
@Entity
@Table(name = "tenant_provisioning")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProvisioning {

    /** In order. Each is idempotent, so a crash or retry re-runs the current one safely. */
    public enum Step { AWAIT_SCHEMAS, CREATE_OWNER, ACTIVATE, INVITE_OWNER, DONE, FAILED }

    @Id
    @Column(name = "tenant_key", length = 31)
    private String tenantKey;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Step step;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "requested_by", length = 64)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
