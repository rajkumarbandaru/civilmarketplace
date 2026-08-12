-- ============================================================================
-- Notification Service - Initial Schema
-- Database: civil_engineer_notifications
-- ============================================================================

-- Notifications table
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

-- Email templates table
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

-- SMS templates table
CREATE TABLE sms_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL UNIQUE,
    body_text VARCHAR(500) NOT NULL,
    variables VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Seed Email Templates
-- ============================================================================
INSERT INTO email_templates (template_name, subject, body_html, variables, is_active) VALUES
('otp-template', 'Your OTP Code - Civil Engineering Marketplace',
 '<!DOCTYPE html><html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;"><div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;"><h1 style="color: white; margin: 0;">Civil Engineering Marketplace</h1></div><div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; border: 1px solid #eee; border-top: none;"><h2>Your OTP Code</h2><p>Use the following OTP to complete your verification:</p><div style="background: #fff; padding: 20px; text-align: center; border-radius: 8px; border: 2px dashed #667eea; margin: 20px 0;"><h1 style="font-size: 36px; letter-spacing: 8px; color: #667eea; margin: 0;">[(${otp})]</h1></div><p>This OTP is valid for [(${expiryMinutes})] minutes.</p><p>If you didn\'t request this, please ignore this email.</p><hr style="border: none; border-top: 1px solid #eee;"><p style="color: #999; font-size: 12px;">&copy; 2026 Civil Engineering Marketplace. All rights reserved.</p></div></body></html>',
 'otp, expiryMinutes', TRUE),

('welcome-template', 'Welcome to Civil Engineering Marketplace!',
 '<!DOCTYPE html><html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;"><div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;"><h1 style="color: white; margin: 0;">Welcome!</h1></div><div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;"><h2>Hello [(${name})]!</h2><p>Thank you for joining Civil Engineering Marketplace.</p><p>You now have access to India\'s largest platform for civil engineering services. Browse professionals, book services, and manage your projects all in one place.</p><a href="https://app.civilengineer.com" style="display: inline-block; background: #667eea; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin-top: 20px;">Get Started</a></div></body></html>',
 'name', TRUE),

('booking-confirmed-template', 'Booking Confirmed - Civil Engineering Marketplace',
 '<!DOCTYPE html><html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;"><div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;"><h1 style="color: white; margin: 0;">Booking Confirmed!</h1></div><div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;"><h2>Hello [(${name})]!</h2><p>Your booking has been confirmed.</p><div style="background: #fff; padding: 15px; border-radius: 8px; border: 1px solid #eee;"><strong>Booking Code:</strong> [(${bookingCode})]</div><p>Track your booking in the app for real-time updates.</p></div></body></html>',
 'name, bookingCode', TRUE),

('payment-received-template', 'Payment Received - Civil Engineering Marketplace',
 '<!DOCTYPE html><html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;"><div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;"><h1 style="color: white; margin: 0;">Payment Received!</h1></div><div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;"><h2>Hello [(${name})]!</h2><p>We have received your payment of <strong>&#8377;[(${amount})]</strong>.</p><p><strong>Payment Code:</strong> [(${paymentCode})]</p><p>Thank you for using Civil Engineering Marketplace.</p></div></body></html>',
 'name, amount, paymentCode', TRUE);
