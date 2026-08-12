package com.civileng.marketplace.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** FR-05's transition log. Written on every status change; never updated or deleted. */
@Entity
@Table(name = "project_status_history", indexes = {
        @Index(name = "idx_history_project", columnList = "project_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20)
    private ProjectStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 20)
    private ProjectStatus toStatus;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;
}
