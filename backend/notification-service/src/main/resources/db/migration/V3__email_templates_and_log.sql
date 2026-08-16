-- ============================================================================
-- Email templates and the delivery log.
--
-- Until now every transactional email was a Thymeleaf file baked into the jar, so changing a
-- single sentence meant a rebuild and a redeploy. `email_templates` holds an editable override
-- per template key: when a row exists and is active its html_body is rendered instead of the
-- classpath file, and deleting the override falls straight back to the shipped default. That
-- fallback is why the built-in rows are seeded from the classpath at startup rather than pasted
-- into this migration — the file on disk stays the single source of truth for the default.
--
-- `email_log` records one row per send attempt so the console can answer "did the customer get
-- the booking confirmation?". Status starts at PENDING and is moved by the sender; the DELIVERED
-- and UNDELIVERED ends only arrive when the provider tells us, via the Brevo webhook.
-- ============================================================================

CREATE TABLE IF NOT EXISTS email_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Matches the file name under resources/templates/email (without .html) for built-ins, e.g.
    -- 'otp-template'. Custom templates use any unused key.
    template_key VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    -- Thymeleaf expressions are allowed here too, so a subject can carry ${bookingCode}.
    subject VARCHAR(300) NOT NULL,
    html_body MEDIUMTEXT NOT NULL,
    -- JSON object of placeholder -> example value, used to render the preview.
    sample_variables TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- TRUE for the eight templates the code sends by name. They can be edited and deactivated
    -- (which reverts to the shipped default) but never deleted, because EmailService still
    -- references the key.
    system_owned BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email_template_key (template_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_key VARCHAR(120) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(300) NOT NULL,
    -- PENDING | SENT | DELIVERED | UNDELIVERED | FAILED | SKIPPED
    status VARCHAR(20) NOT NULL,
    -- smtp | brevo | log
    provider VARCHAR(20) NOT NULL,
    -- Brevo's message id, the key the delivery webhook arrives under. Null for SMTP.
    provider_message_id VARCHAR(200),
    -- Why a FAILED/UNDELIVERED row failed, straight from the provider.
    error_message VARCHAR(1000),
    -- Set when a human triggered the send from the console rather than an app event.
    triggered_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email_log_created (created_at),
    INDEX idx_email_log_status (status),
    INDEX idx_email_log_recipient (recipient),
    INDEX idx_email_log_template (template_key),
    INDEX idx_email_log_message_id (provider_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
