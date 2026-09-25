-- ============================================================================
-- The analytics warehouse (architecture 02 §8, Diagram 20): facts derived by change-data capture
-- from every tenant's schemas on every cluster. SHARED/derived data: keyed and partitioned by
-- tenant_key, holding no direct identifiers (customers and payers are keyed pseudonyms). A
-- tenant's rows are deleted with the tenant.
-- ============================================================================
CREATE TABLE fact_bookings (
    tenant_key       VARCHAR(31)    NOT NULL,
    booking_id       BIGINT         NOT NULL,
    status           VARCHAR(30)    NULL,
    service_category VARCHAR(100)   NULL,
    city             VARCHAR(100)   NULL,
    payment_status   VARCHAR(30)    NULL,
    total_amount     DECIMAL(12, 2) NULL,
    is_emergency     BOOLEAN        NULL,
    customer_token   CHAR(16)       NULL,
    created_at       DATETIME(3)    NULL,
    updated_at       DATETIME(3)    NULL,
    deleted_at       DATETIME(3)    NULL,
    source_cluster   VARCHAR(40)    NOT NULL,
    captured_at      DATETIME(3)    NOT NULL,
    PRIMARY KEY (tenant_key, booking_id),
    INDEX idx_fb_created (tenant_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY KEY (tenant_key) PARTITIONS 16;

CREATE TABLE fact_payments (
    tenant_key      VARCHAR(31)    NOT NULL,
    payment_id      BIGINT         NOT NULL,
    reference_type  VARCHAR(30)    NULL,
    status          VARCHAR(30)    NULL,
    total_amount    DECIMAL(12, 2) NULL,
    currency        VARCHAR(10)    NULL,
    payer_token     CHAR(16)       NULL,
    paid_at         DATETIME(3)    NULL,
    created_at      DATETIME(3)    NULL,
    deleted_at      DATETIME(3)    NULL,
    source_cluster  VARCHAR(40)    NOT NULL,
    captured_at     DATETIME(3)    NOT NULL,
    PRIMARY KEY (tenant_key, payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY KEY (tenant_key) PARTITIONS 16;

CREATE TABLE fact_purchase_orders (
    tenant_key      VARCHAR(31)    NOT NULL,
    po_id           BIGINT         NOT NULL,
    buyer_org_id    BIGINT         NULL,
    supplier_org_id BIGINT         NULL,
    status          VARCHAR(30)    NULL,
    total           DECIMAL(15, 2) NULL,
    created_at      DATETIME(3)    NULL,
    deleted_at      DATETIME(3)    NULL,
    source_cluster  VARCHAR(40)    NOT NULL,
    captured_at     DATETIME(3)    NOT NULL,
    PRIMARY KEY (tenant_key, po_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY KEY (tenant_key) PARTITIONS 16;

-- Where each cluster's binlog was read up to, so a restart resumes exactly there.
CREATE TABLE cdc_offsets (
    cluster_id   VARCHAR(40)  NOT NULL PRIMARY KEY,
    binlog_file  VARCHAR(100) NOT NULL,
    binlog_pos   BIGINT       NOT NULL,
    events       BIGINT       NOT NULL DEFAULT 0,
    last_event_at DATETIME(3) NULL,
    updated_at   DATETIME(3)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
