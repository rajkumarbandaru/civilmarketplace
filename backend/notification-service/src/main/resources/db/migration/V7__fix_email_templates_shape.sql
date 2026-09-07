-- ============================================================================
-- Reconciles `email_templates` with the shape the entity actually maps.
--
-- The bug this fixes was invisible until multi-tenancy: V1 creates an `email_templates` with the
-- old (template_name, body_html, is_active) shape, and V3 — which defines the real one — guards
-- its own CREATE with IF NOT EXISTS, so on a schema built from scratch V3 silently keeps V1's
-- table and the service fails at runtime with "Unknown column 'active'".
--
-- The legacy single-tenant database escaped it because V1 was applied there before that block was
-- added to the file, so V3 genuinely did create the table. Every schema provisioned from scratch
-- since — which is every tenant schema — gets the broken one. Fixing V1 or V3 in place would
-- change an applied migration's checksum, so the correction is additive.
--
-- Dropping rather than altering is safe here: V3 deliberately seeds nothing, because the built-in
-- templates are loaded from the classpath at startup and the table only ever holds overrides. Any
-- rows present are V1's seed, which the code never reads — it looks templates up by template_key,
-- a column V1's table does not even have.
-- ============================================================================

DROP TABLE IF EXISTS email_templates;

CREATE TABLE email_templates (
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
    -- TRUE for the templates the code sends by name. They can be edited and deactivated (which
    -- reverts to the shipped default) but never deleted, because EmailService still references
    -- the key.
    system_owned BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email_template_key (template_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
