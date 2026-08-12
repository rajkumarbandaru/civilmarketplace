package com.civileng.marketplace.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A drawing, permit or approval attached to a project. Stores an object-storage reference only
 * (ENT·01 FR-06) — the same rule KycDocument follows. Nothing binary belongs in this table.
 */
@Entity
@Table(name = "project_documents", indexes = {
        @Index(name = "idx_document_project", columnList = "project_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "file_ref", nullable = false, length = 500)
    private String fileRef;

    @Column(name = "doc_type", nullable = false, length = 50)
    private String docType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
