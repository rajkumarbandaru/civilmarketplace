-- ============================================================================
-- Notification Service - Initial Schema
-- ============================================================================

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    data VARCHAR(5000),
    reference_type VARCHAR(50),
    reference_id BIGINT,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_user (user_id),
    INDEX idx_notification_read (user_id, is_read),
    INDEX idx_notification_type (type),
    INDEX idx_notification_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL UNIQUE,
    subject VARCHAR(255) NOT NULL,
    body_html TEXT NOT NULL,
    body_text TEXT,
    variables VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO email_templates (template_name, subject, body_html, variables, is_active) VALUES
('otp-template', 'Your OTP Code - Civil Engineering Marketplace',
 '<!DOCTYPE html><html><body style="font-family: Arial, sans-serif;"><div style="background: linear-gradient(135deg, #667eea, #764ba2); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;"><h1 style="color: white;">Civil Engineering Marketplace</h1></div><div style="background: #f9f9f9; padding: 30px;"><h2>Your OTP Code</h2><p>Use the following OTP to complete your verification:</p><div style="background: #fff; padding: 20px; text-align: center; border: 2px dashed #667eea; margin: 20px 0;"><h1 style="font-size: 36px; letter-spacing: 8px; color: #667eea; margin: 0;">[(${otp})]</h1></div><p>This OTP is valid for [(${expiryMinutes})] minutes.</p></div></body></html>',
 'otp, expiryMinutes', TRUE),

('welcome-template', 'Welcome to Civil Engineering Marketplace!',
 '<!DOCTYPE html><html><body style="font-family: Arial, sans-serif;"><div style="background: linear-gradient(135deg, #667eea, #764ba2); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;"><h1 style="color: white;">Welcome!</h1></div><div style="background: #f9f9f9; padding: 30px;"><h2>Hello [(${name})]!</h2><p>Thank you for joining Civil Engineering Marketplace.</p></div></body></html>',
 'name', TRUE);
