package com.civileng.marketplace.tenant.placement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One tenant's move between clusters, advanced a step at a time by {@link MoveService}. */
@Entity
@Table(name = "tenant_moves")
@Getter
@Setter
@NoArgsConstructor
public class TenantMove {

    /**
     * COPY while live → FREEZE (writes paused) → SYNC tables that changed since → VERIFY checksums
     * → FLIP the placement → AWAIT_ACKS from every service → RESUME writes → DONE.
     */
    public enum Step { COPY, FREEZE, SYNC, VERIFY, FLIP, AWAIT_ACKS, RESUME, DONE, FAILED, ROLLED_BACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 31)
    private String tenantKey;

    @Column(name = "source_cluster_id", nullable = false, length = 40)
    private String sourceClusterId;

    @Column(name = "target_cluster_id", nullable = false, length = 40)
    private String targetClusterId;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Step step;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "target_epoch")
    private Long targetEpoch;

    @Column(name = "schemas_copied", nullable = false)
    private int schemasCopied;

    @Column(name = "tables_copied", nullable = false)
    private int tablesCopied;

    @Column(name = "rows_copied", nullable = false)
    private long rowsCopied;

    @Column(name = "tables_resynced", nullable = false)
    private int tablesResynced;

    @Column(columnDefinition = "TEXT")
    private String report;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "resumed_at")
    private LocalDateTime resumedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "source_dropped_at")
    private LocalDateTime sourceDroppedAt;

    public boolean running() {
        return step != Step.DONE && step != Step.FAILED && step != Step.ROLLED_BACK;
    }
}
