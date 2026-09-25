-- ============================================================================
-- Placement (architecture 02 §6.2, Diagram 20): which MySQL cluster holds each tenant's schemas,
-- and the moves between them. The control plane itself (this schema) stays on the first cluster.
--
-- Services route by this map: tenant → cluster → schema. A move copies the schemas, pauses the
-- tenant's writes (MAINTENANCE), flips the placement, waits for every service to route to the new
-- cluster, then resumes.
-- ============================================================================

CREATE TABLE db_clusters (
    cluster_id VARCHAR(40)  NOT NULL PRIMARY KEY,
    cell       VARCHAR(40)  NOT NULL,
    host       VARCHAR(120) NOT NULL,
    port       INT          NOT NULL DEFAULT 3306,
    -- SHARED: any number of Standard tenants. DEDICATED: one tenant (tier T2).
    kind       VARCHAR(20)  NOT NULL DEFAULT 'SHARED',
    -- ACTIVE takes tenants; DRAINING keeps what it has; RETIRED holds none.
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    capacity   INT          NOT NULL DEFAULT 500,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_cluster_endpoint (host, port)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- cluster-a is where everything lives today; cluster-b is a second shared cluster (the mysql and
-- mysql-b containers locally). A DEDICATED cluster (tier T2) is registered the same way, with
-- kind DEDICATED: it is just a cluster with one tenant on it (02 §6.2).
INSERT INTO db_clusters (cluster_id, cell, host, port, kind) VALUES
('cluster-a', 'cell-1', 'mysql',   3306, 'SHARED'),
('cluster-b', 'cell-1', 'mysql-b', 3306, 'SHARED');

ALTER TABLE tenants
    ADD COLUMN tier            VARCHAR(20) NOT NULL DEFAULT 'STANDARD' AFTER plan,
    ADD COLUMN db_cluster_id   VARCHAR(40) NOT NULL DEFAULT 'cluster-a' AFTER tier,
    ADD COLUMN placement_epoch BIGINT      NOT NULL DEFAULT 0 AFTER db_cluster_id,
    ADD CONSTRAINT fk_tenant_cluster FOREIGN KEY (db_cluster_id) REFERENCES db_clusters (cluster_id);

CREATE TABLE tenant_moves (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key        VARCHAR(31)  NOT NULL,
    source_cluster_id VARCHAR(40)  NOT NULL,
    target_cluster_id VARCHAR(40)  NOT NULL,
    -- COPY | FREEZE | SYNC | VERIFY | FLIP | AWAIT_ACKS | RESUME | DONE | FAILED | ROLLED_BACK
    step              VARCHAR(20)  NOT NULL,
    attempts          INT          NOT NULL DEFAULT 0,
    last_error        VARCHAR(1000) NULL,
    target_epoch      BIGINT       NULL,
    schemas_copied    INT          NOT NULL DEFAULT 0,
    tables_copied     INT          NOT NULL DEFAULT 0,
    rows_copied       BIGINT       NOT NULL DEFAULT 0,
    tables_resynced   INT          NOT NULL DEFAULT 0,
    -- JSON: per-schema table checksums at verification, and services that acknowledged.
    report            TEXT         NULL,
    requested_by      VARCHAR(64)  NOT NULL,
    started_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    frozen_at         TIMESTAMP(3) NULL,
    resumed_at        TIMESTAMP(3) NULL,
    finished_at       TIMESTAMP(3) NULL,
    source_dropped_at TIMESTAMP(3) NULL,
    INDEX idx_move_tenant (tenant_key),
    INDEX idx_move_step (step)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_move_acks (
    move_id    BIGINT       NOT NULL,
    service    VARCHAR(60)  NOT NULL,
    epoch      BIGINT       NOT NULL,
    acked_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (move_id, service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
