package com.civileng.marketplace.auditservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A right-to-erasure request. The request log itself is never erased — deleting it would
 * destroy the evidence that the erasure was honoured (SRS OPS-03 FR-07).
 */
@Entity
@Table(name = "erasure_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErasureRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "RECEIVED";

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
