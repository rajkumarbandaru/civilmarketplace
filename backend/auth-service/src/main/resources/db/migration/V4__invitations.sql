-- ============================================================================
-- One-time invitations: how a new workspace's owner gets their first password. The account is
-- created with no password; the owner sets one through a single-use link. Only the SHA-256 of the
-- token is stored, so a database read cannot be turned into a working link.
-- ============================================================================

CREATE TABLE user_invitations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    used_at     TIMESTAMP   NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invitation_token UNIQUE (token_hash),
    CONSTRAINT fk_invitation_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_invitation_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
