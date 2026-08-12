package com.civileng.marketplace.auditservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An append-only audit record. Nothing in this service updates or deletes rows of this table —
 * treat any code that does as a compliance defect.
 */
@Entity
@Table(name = "audit_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_service", nullable = false, length = 60)
    private String sourceService;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "subject_user_id")
    private Long subjectUserId;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", insertable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;
}
