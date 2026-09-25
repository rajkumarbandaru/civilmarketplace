-- ============================================================================
-- Custom domains (architecture 06 §5, Diagram 16; 03 §7 Domain Manager): a tenant's own host names,
-- verified by DNS, served with an ACME certificate, re-checked daily.
--
--   PENDING_VERIFICATION → VERIFIED → CERT_ISSUING → ACTIVE ⇄ DEGRADED
--                                                       ↘ FAILED / REMOVED
-- Only ACTIVE and DEGRADED hosts route to the tenant (DEGRADED keeps serving within its grace).
-- ============================================================================
CREATE TABLE tenant_domains (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key          VARCHAR(31)   NOT NULL,
    host                VARCHAR(253)  NOT NULL,
    -- WEB (the tenant's site and app) or ADMIN (its console)
    surface             VARCHAR(10)   NOT NULL DEFAULT 'WEB',
    status              VARCHAR(30)   NOT NULL,
    -- The value the tenant publishes at _platform-verify.<host> to prove it controls the name.
    verification_token  VARCHAR(64)   NOT NULL,
    cert_serial         VARCHAR(64)   NULL,
    cert_issuer         VARCHAR(255)  NULL,
    cert_not_after      TIMESTAMP(3)  NULL,
    attempts            INT           NOT NULL DEFAULT 0,
    last_error          VARCHAR(1000) NULL,
    last_checked_at     TIMESTAMP(3)  NULL,
    degraded_since      TIMESTAMP(3)  NULL,
    created_by          VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at        TIMESTAMP(3)  NULL,
    removed_at          TIMESTAMP(3)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    -- One live claim per host: a removed domain may be claimed again.
    live_host           VARCHAR(253)  GENERATED ALWAYS AS (CASE WHEN status <> 'REMOVED' THEN host END) STORED,
    UNIQUE KEY uk_domain_live_host (live_host),
    INDEX idx_domain_tenant (tenant_key),
    INDEX idx_domain_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- HTTP-01 answers the ACME server fetches from http://<host>/.well-known/acme-challenge/<token>.
-- In the database, so whichever tenant-service instance the edge reaches can answer.
CREATE TABLE acme_challenges (
    token             VARCHAR(128)  NOT NULL PRIMARY KEY,
    host              VARCHAR(253)  NOT NULL,
    key_authorization VARCHAR(512)  NOT NULL,
    created_at        TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The platform's ACME account key (one per ACME directory).
CREATE TABLE acme_accounts (
    directory   VARCHAR(255)  NOT NULL PRIMARY KEY,
    key_pem     TEXT          NOT NULL,
    created_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
