package com.civileng.marketplace.tenant.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A tenant's own host name, through verification and certification to serving. */
@Entity
@Table(name = "tenant_domains")
@Getter
@Setter
@NoArgsConstructor
public class TenantDomain {

    public enum Status { PENDING_VERIFICATION, VERIFIED, CERT_ISSUING, ACTIVE, DEGRADED, FAILED, REMOVED }

    public enum Surface { WEB, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 31)
    private String tenantKey;

    @Column(nullable = false, length = 253)
    private String host;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Surface surface = Surface.WEB;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "verification_token", nullable = false, length = 64)
    private String verificationToken;

    @Column(name = "cert_serial", length = 64)
    private String certSerial;

    @Column(name = "cert_issuer", length = 255)
    private String certIssuer;

    @Column(name = "cert_not_after")
    private LocalDateTime certNotAfter;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "degraded_since")
    private LocalDateTime degradedSince;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Version
    private Long version;

    /** Routes to the tenant: serving, or still serving inside its grace period. */
    public boolean serving() {
        return status == Status.ACTIVE || status == Status.DEGRADED;
    }
}
