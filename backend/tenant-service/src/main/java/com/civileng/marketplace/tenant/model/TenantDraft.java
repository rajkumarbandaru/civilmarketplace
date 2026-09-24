package com.civileng.marketplace.tenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The Create Platform wizard's autosaved state: every section as one JSON document, versioned so
 * two operators editing the same draft cannot silently overwrite each other.
 */
@Entity
@Table(name = "tenant_drafts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDraft {

    public enum Status { OPEN, CREATED, DISCARDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String data;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "tenant_key", length = 31)
    private String tenantKey;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
