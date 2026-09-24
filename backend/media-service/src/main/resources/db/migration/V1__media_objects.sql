CREATE TABLE media_objects (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    owner_user_id     BIGINT       NOT NULL,
    purpose           VARCHAR(32)  NOT NULL,
    visibility        VARCHAR(16)  NOT NULL,
    bucket            VARCHAR(64)  NOT NULL,
    object_key        VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    declared_size     BIGINT       NOT NULL,
    size_bytes        BIGINT,
    status            VARCHAR(16)  NOT NULL,
    rejection_reason  VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP    NULL,
    deleted_at        TIMESTAMP    NULL,
    INDEX idx_media_owner (owner_user_id, purpose),
    -- The orphan sweep reads PENDING rows by age.
    INDEX idx_media_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
