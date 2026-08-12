package com.civileng.marketplace.audit.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The wire contract for an audit event. Published to {@link AuditTopics#AUDIT_EVENTS} by any
 * service that reads or writes sensitive data, and consumed by audit-service.
 *
 * <p>DPDP compliance requires logging <em>reads</em> of personal data, not just writes — hence
 * {@link AuditAction#READ}. Keep this class backwards-compatible: audit-service deserialises
 * events that may have been produced by an older build.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventMessage {

    /** Service that produced the event, e.g. "user-service". */
    private String sourceService;

    /** Authenticated user who performed the action; null for system/scheduled actions. */
    private Long actorId;

    private String actorRole;

    private AuditAction action;

    /** Entity class touched, e.g. "KycDocument". */
    private String entityType;

    /** Identifier of the entity instance; null for bulk/list reads. */
    private String entityId;

    /**
     * Subject whose personal data this concerns — the basis of a right-to-access export.
     * For a KYC approval this is the document owner, not the reviewing admin.
     */
    private Long subjectUserId;

    /** JSON or short text describing prior state. Never include raw credentials. */
    private String beforeState;

    private String afterState;

    /** Why the action happened, where the caller supplies one (e.g. a rejection reason). */
    private String reason;

    /** How many records a list/bulk read returned — feeds bulk-access anomaly detection. */
    private Integer recordCount;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
