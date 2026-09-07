-- The platform tenant registry. This table is NOT tenant-scoped: it is the one place that knows
-- which tenants exist, and every tenanted service reads it at boot to decide which schemas to
-- migrate.

CREATE TABLE tenants (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key      VARCHAR(31)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    subdomain       VARCHAR(63)  NOT NULL,
    custom_domain   VARCHAR(253) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    contact_email   VARCHAR(150) NOT NULL,
    plan            VARCHAR(30)  NOT NULL DEFAULT 'STANDARD',
    -- Which vertical this tenant runs. Tenants are not all the same product: a civil-engineering
    -- marketplace and a hostel fee-collection platform share auth/users/payments/notifications
    -- but not bookings, projects or reviews.
    vertical        VARCHAR(40)  NOT NULL DEFAULT 'CIVIL_MARKETPLACE',
    -- CSV of enabled module keys, resolved from the vertical at creation and editable after.
    -- The gateway refuses routes for a module a tenant does not have, so a disabled service is
    -- unreachable rather than merely hidden in the UI.
    enabled_modules TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NULL,
    CONSTRAINT uk_tenants_key UNIQUE (tenant_key),
    CONSTRAINT uk_tenants_subdomain UNIQUE (subdomain),
    CONSTRAINT uk_tenants_custom_domain UNIQUE (custom_domain),
    INDEX idx_tenants_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The operator tenant. It owns the platform console — the only place tenants can be created —
-- and doubles as the bootstrap schema every service validates its mappings against, so it must
-- exist before any tenanted service starts.
INSERT INTO tenants (tenant_key, name, subdomain, status, contact_email, plan, vertical,
                     enabled_modules, created_by)
VALUES ('platform', 'Platform Operator', 'platform', 'ACTIVE',
        'ops@civilengineering.local', 'OPERATOR', 'CIVIL_MARKETPLACE',
        'auth,users,payments,notifications,support,admin,audit,messaging,bookings,projects,reviews,search',
        'system');
