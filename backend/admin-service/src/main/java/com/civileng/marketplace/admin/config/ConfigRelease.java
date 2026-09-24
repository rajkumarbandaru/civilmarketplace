package com.civileng.marketplace.admin.config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One save: the document versions published together. Immutable once written. */
@Entity
@Table(name = "config_releases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigRelease {

    public enum Source { SEED, ONBOARDING, OPERATOR, CONSOLE, ROLLBACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String scope;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "rollback_of_release_id")
    private Long rollbackOfReleaseId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
