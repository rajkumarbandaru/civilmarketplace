-- Per-tenant provider credentials: payment gateway, mail, SMS, WhatsApp, AI.
--
-- Until now every tenant ran on one global Razorpay key, one SMTP login, one Twilio account and one
-- Gemini key from config-repo — tenant B's customers paid into whichever merchant account the
-- platform was configured with. Each tenant now brings its own account, one row per capability.
--
-- secrets_ciphertext is AES-256-GCM (tenant-common IntegrationCipher) with the tenant key and
-- capability bound in as AAD, so a value copied onto another tenant's row will not decrypt.
-- secret_hints_json holds only masked last-four hints for the console; no API returns a secret.
--
-- webhook_token is the opaque path segment a provider calls back on. Webhooks carry no JWT and no
-- tenant header, so this token is how an inbound Razorpay event finds its tenant — and its
-- tenant's webhook secret to verify the signature with.
CREATE TABLE tenant_integrations (
    tenant_key          VARCHAR(31)  NOT NULL,
    capability          VARCHAR(20)  NOT NULL,
    mode                VARCHAR(20)  NOT NULL,
    provider            VARCHAR(40)  NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    settings_json       TEXT         NULL,
    secrets_ciphertext  TEXT         NULL,
    secret_hints_json   TEXT         NULL,
    webhook_token       VARCHAR(64)  NULL,
    updated_by          VARCHAR(64)  NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_key, capability),
    UNIQUE KEY uk_tenant_integrations_webhook_token (webhook_token),
    CONSTRAINT fk_tenant_integrations_tenant FOREIGN KEY (tenant_key) REFERENCES tenants (tenant_key)
);

-- Existing customer tenants keep sending mail and using the AI assistant on the platform's account,
-- as they did before this migration — both capabilities allow sharing. Payment, SMS and WhatsApp
-- are deliberately NOT seeded: those must be the tenant's own account, and until one is connected
-- the feature reports "not configured" rather than borrowing the platform's.
INSERT INTO tenant_integrations (tenant_key, capability, mode, enabled, updated_by)
SELECT tenant_key, 'EMAIL', 'PLATFORM_SHARED', TRUE, 'migration-V5'
FROM tenants WHERE tenant_key <> 'platform';

INSERT INTO tenant_integrations (tenant_key, capability, mode, enabled, updated_by)
SELECT tenant_key, 'AI', 'PLATFORM_SHARED', TRUE, 'migration-V5'
FROM tenants WHERE tenant_key <> 'platform';
