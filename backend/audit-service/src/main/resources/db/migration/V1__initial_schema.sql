-- ============================================================================
-- Audit Service - Initial Schema
-- Append-only. Nothing in this service issues UPDATE or DELETE against
-- audit_events; erasure_requests are likewise never erased (SRS OPS-03).
-- ============================================================================

CREATE TABLE audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_service VARCHAR(60) NOT NULL,
    actor_id BIGINT,
    actor_role VARCHAR(40),
    action VARCHAR(30) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80),
    subject_user_id BIGINT,
    before_state TEXT,
    after_state TEXT,
    reason VARCHAR(500),
    record_count INT,
    occurred_at TIMESTAMP(3) NOT NULL,
    recorded_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    -- Hash chain: each row commits to its predecessor, so silently rewriting or
    -- deleting history becomes detectable (SRS OPS-03 FR-10, verifiable integrity).
    previous_hash VARCHAR(64),
    event_hash VARCHAR(64) NOT NULL,
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_subject (subject_user_id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE erasure_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    reason VARCHAR(500),
    handled_by BIGINT,
    handled_at TIMESTAMP NULL,
    resolution_note VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_erasure_user (user_id),
    INDEX idx_erasure_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_anomaly_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT,
    entity_type VARCHAR(80) NOT NULL,
    records_accessed INT NOT NULL,
    window_minutes INT NOT NULL,
    detail VARCHAR(500),
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_anomaly_actor (actor_id),
    INDEX idx_anomaly_ack (acknowledged)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
