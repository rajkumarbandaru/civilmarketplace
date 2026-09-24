-- ============================================================================
-- Plans, subscriptions and grants (architecture 08): what a tenant is ENTITLED to, kept apart
-- from what it has chosen to switch on (tenants.enabled_modules) and from operational flags.
--
-- The modules a tenant actually runs are its choices ∩ its entitlement. Choices survive a
-- downgrade, so an upgrade brings them back; entitlement is derived from the subscription's plan
-- version, its add-ons and any unexpired grants.
-- ============================================================================

-- Immutable once any tenant subscribes: a change is a new version. Limits are JSON; a limit that
-- is absent is unlimited.
CREATE TABLE plans (
    plan_key   VARCHAR(40)  NOT NULL,
    version    INT          NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    features   TEXT         NOT NULL,
    limits     TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (plan_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO plans (plan_key, version, name, features, limits) VALUES
('starter', 1, 'Starter',
 'bookings,reviews,search,residents,feeplans,invoices,collections,properties,listings',
 '{"staff.seats":5,"bookings.monthly":500,"media.storageMb":5120}'),
('professional', 1, 'Professional',
 'bookings,reviews,search,projects,residents,feeplans,invoices,collections,properties,listings,leases,valuations',
 '{"staff.seats":50,"bookings.monthly":5000,"media.storageMb":51200}'),
('enterprise', 1, 'Enterprise',
 'bookings,reviews,search,projects,residents,feeplans,invoices,collections,properties,listings,leases,valuations,landrecords',
 '{}');

CREATE TABLE tenant_subscriptions (
    tenant_key   VARCHAR(31)  NOT NULL PRIMARY KEY,
    plan_key     VARCHAR(40)  NOT NULL,
    plan_version INT          NOT NULL,
    -- TRIALING | ACTIVE | PAST_DUE | SUSPENDED | CANCELED
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    add_ons      VARCHAR(500) NOT NULL DEFAULT '',
    updated_by   VARCHAR(64)  NULL,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_key, plan_version) REFERENCES plans (plan_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Exceptions: a feature (limit_value NULL) or a raised limit, always expiring, always attributed.
CREATE TABLE tenant_grants (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key        VARCHAR(31)  NOT NULL,
    feature           VARCHAR(60)  NOT NULL,
    limit_value       BIGINT       NULL,
    expires_at        TIMESTAMP(3) NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    granted_by        VARCHAR(64)  NULL,
    created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at        TIMESTAMP(3) NULL,
    revoked_by        VARCHAR(64)  NULL,
    expiry_announced  BOOLEAN      NOT NULL DEFAULT FALSE,
    INDEX idx_grants_tenant (tenant_key),
    INDEX idx_grants_expiry (expires_at, expiry_announced)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every existing tenant is grandfathered onto Enterprise: nothing it runs today may disappear
-- because plans started being enforced.
INSERT INTO tenant_subscriptions (tenant_key, plan_key, plan_version, status, updated_by)
SELECT tenant_key, 'enterprise', 1, 'ACTIVE', 'migration' FROM tenants;
