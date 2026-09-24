package com.civileng.marketplace.tenant.entitlement;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An exception to the plan: a feature ({@code limitValue} null) or a raised limit. Always
 * expiring, always attributed — a grant that never expires is revenue leakage (08 §7).
 */
@Entity
@Table(name = "tenant_grants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 31)
    private String tenantKey;

    @Column(nullable = false, length = 60)
    private String feature;

    @Column(name = "limit_value")
    private Long limitValue;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "granted_by", length = 64)
    private String grantedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @Column(name = "expiry_announced", nullable = false)
    private boolean expiryAnnounced;

    public boolean activeAt(LocalDateTime when) {
        return revokedAt == null && expiresAt.isAfter(when);
    }
}
