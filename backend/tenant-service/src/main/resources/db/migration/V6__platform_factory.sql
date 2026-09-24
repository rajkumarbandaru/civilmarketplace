-- ============================================================================
-- Platform Factory (architecture 03): drafts, lifecycle and the provisioning saga.
--
-- A tenant is no longer ACTIVE the moment its row is written. It is created DRAFT (key and
-- subdomain reserved, nothing provisioned), published into PROVISIONING while every service
-- builds its storage and acknowledges, and only then ACTIVE. PENDING — the old "created but not
-- provisioned" — is PROVISIONING now.
-- ============================================================================

UPDATE tenants SET status = 'PROVISIONING' WHERE status = 'PENDING';

-- Who the workspace belongs to. The account itself lives in the tenant's auth schema and is
-- created at publish; these are what the invitation is sent to.
ALTER TABLE tenants
    ADD COLUMN owner_name  VARCHAR(120) NULL AFTER contact_email,
    ADD COLUMN owner_email VARCHAR(150) NULL AFTER owner_name;

CREATE TABLE tenant_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key  VARCHAR(31)  NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    actor       VARCHAR(64)  NULL,
    reason      VARCHAR(500) NULL,
    changed_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_status_history_tenant (tenant_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The wizard's autosaved state. Not a tenant: no key is reserved and nothing exists anywhere
-- until "Create" turns it into a DRAFT tenant.
CREATE TABLE tenant_drafts (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(120) NULL,
    data        MEDIUMTEXT   NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    tenant_key  VARCHAR(31)  NULL,
    created_by  VARCHAR(64)  NULL,
    updated_by  VARCHAR(64)  NULL,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_drafts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The saga's persisted state, so a restarted tenant-service resumes a half-provisioned tenant.
CREATE TABLE tenant_provisioning (
    tenant_key    VARCHAR(31)   NOT NULL PRIMARY KEY,
    step          VARCHAR(30)   NOT NULL,
    attempts      INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000) NULL,
    owner_user_id BIGINT        NULL,
    requested_by  VARCHAR(64)   NULL,
    requested_at  TIMESTAMP(3)  NOT NULL,
    finished_at   TIMESTAMP(3)  NULL,
    updated_at    TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_service_acks (
    tenant_key  VARCHAR(31)   NOT NULL,
    service     VARCHAR(60)   NOT NULL,
    ok          BOOLEAN       NOT NULL,
    error       VARCHAR(1000) NULL,
    received_at TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_key, service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
