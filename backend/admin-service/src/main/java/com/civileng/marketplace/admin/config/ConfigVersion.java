package com.civileng.marketplace.admin.config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** An immutable snapshot of one document in one scope. */
@Entity
@Table(name = "config_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(nullable = false, length = 60)
    private String scope;

    @Column(nullable = false, length = 20)
    private String document;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private Integer schemaVersion = 1;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "validation_report", columnDefinition = "TEXT")
    private String validationReport;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
