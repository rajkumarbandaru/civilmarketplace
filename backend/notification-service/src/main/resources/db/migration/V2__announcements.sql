-- ============================================================================
-- Announcements — one-click admin broadcast (SRS ENT·04), extending
-- notification-service rather than a new service: fan-out reuses the existing
-- Notification row/is_read shape, one row per recipient, same as any other
-- notification type this service already creates.
-- ============================================================================

CREATE TABLE announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    -- Comma-separated role names, or the literal '*' for every ACTIVE user — same convention as
    -- admin-service's ui_menu_items.default_roles, so admins reading both consoles see one rule.
    target_roles VARCHAR(500) NOT NULL,
    created_by BIGINT NOT NULL,
    recipient_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_announcement_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
