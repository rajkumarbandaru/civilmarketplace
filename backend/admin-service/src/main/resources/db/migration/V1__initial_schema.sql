-- Admin Service Initial Schema
-- Stores admin audit logs and platform settings

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    admin_name VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL COMMENT 'CREATE, UPDATE, DELETE, STATUS_CHANGE, LOGIN, etc.',
    entity_type VARCHAR(100) NOT NULL COMMENT 'USER, BOOKING, CATEGORY, SETTINGS, etc.',
    entity_id BIGINT,
    description VARCHAR(1000),
    details JSON COMMENT 'Additional details stored as JSON',
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_admin (admin_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS platform_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    setting_type VARCHAR(50) DEFAULT 'STRING' COMMENT 'STRING, NUMBER, BOOLEAN, JSON',
    description VARCHAR(500),
    is_encrypted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_settings_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO platform_settings (setting_key, setting_value, setting_type, description) VALUES
('platform.name', 'Civil Engineering Marketplace', 'STRING', 'Platform display name'),
('platform.commission_percentage', '5.0', 'NUMBER', 'Platform commission fee percentage'),
('platform.gst_percentage', '18.0', 'NUMBER', 'GST percentage applied to fees'),
('platform.max_active_bookings_per_worker', '5', 'NUMBER', 'Maximum active bookings per worker'),
('platform.worker_verification_required', 'true', 'BOOLEAN', 'Require worker verification before accepting bookings'),
('platform.support_email', 'support@civilengmarketplace.com', 'STRING', 'Platform support email'),
('platform.support_phone', '+91-1800-123-4567', 'STRING', 'Platform support phone number'),
('platform.currency', 'INR', 'STRING', 'Default currency'),
('platform.default_country', 'India', 'STRING', 'Default country for new users');
