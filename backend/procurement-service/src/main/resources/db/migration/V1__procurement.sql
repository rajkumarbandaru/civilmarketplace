-- ============================================================================
-- B2B procurement (architecture 07 §2): organizations, their relationships, and the
-- RFQ → Quotation → Purchase Order → Goods Receipt → Invoice chain with a three-way match.
--
-- One schema per tenant, like every tenant-scoped service: relationships and trade are within one
-- tenant (decision D4). A person acts for an organization through membership; the platform user
-- account is referenced by id, and by email until that person first signs in.
-- ============================================================================

CREATE TABLE organizations (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(160)   NOT NULL,
    gstin              VARCHAR(15)    NULL,
    -- BUYER, SUPPLIER, CONTRACTOR, EQUIPMENT_PROVIDER, comma-separated
    capabilities       VARCHAR(200)   NOT NULL,
    -- A purchase order above this needs a second person's approval. NULL: the service default.
    approval_threshold DECIMAL(15, 2) NULL,
    created_by         BIGINT         NOT NULL,
    created_at         TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version            BIGINT         NOT NULL DEFAULT 0,
    UNIQUE KEY uk_org_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE org_members (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    email           VARCHAR(255) NOT NULL,
    -- NULL until the person signs in for the first time after being added.
    user_id         BIGINT       NULL,
    -- OWNER | APPROVER | MEMBER
    role            VARCHAR(20)  NOT NULL,
    added_by        BIGINT       NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_member_email (organization_id, email),
    INDEX idx_member_user (user_id),
    INDEX idx_member_email (email),
    CONSTRAINT fk_member_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Directed: "from" is the organization that declared it.
CREATE TABLE org_relationships (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_org_id  BIGINT       NOT NULL,
    to_org_id    BIGINT       NOT NULL,
    -- PREFERRED_SUPPLIER | BLOCKED
    type         VARCHAR(30)  NOT NULL,
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_relationship (from_org_id, to_org_id),
    CONSTRAINT fk_rel_from FOREIGN KEY (from_org_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_rel_to FOREIGN KEY (to_org_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rfqs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    number         VARCHAR(20)  NULL,
    buyer_org_id   BIGINT       NOT NULL,
    title          VARCHAR(200) NOT NULL,
    delivery_site  VARCHAR(300) NULL,
    needed_by      DATE         NULL,
    -- What this is for, e.g. the customer booking the buyer is fulfilling.
    reference      VARCHAR(120) NULL,
    -- OPEN | AWARDED | CANCELLED
    status         VARCHAR(20)  NOT NULL,
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version        BIGINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rfq_number (number),
    INDEX idx_rfq_buyer (buyer_org_id),
    CONSTRAINT fk_rfq_buyer FOREIGN KEY (buyer_org_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rfq_lines (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id      BIGINT         NOT NULL,
    line_no     INT            NOT NULL,
    description VARCHAR(300)   NOT NULL,
    quantity    DECIMAL(15, 3) NOT NULL,
    uom         VARCHAR(20)    NOT NULL,
    CONSTRAINT fk_rfq_line FOREIGN KEY (rfq_id) REFERENCES rfqs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rfq_invitations (
    rfq_id          BIGINT NOT NULL,
    supplier_org_id BIGINT NOT NULL,
    PRIMARY KEY (rfq_id, supplier_org_id),
    INDEX idx_invitation_supplier (supplier_org_id),
    CONSTRAINT fk_invitation_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_org FOREIGN KEY (supplier_org_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quotations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id          BIGINT         NOT NULL,
    supplier_org_id BIGINT         NOT NULL,
    -- SUBMITTED | ACCEPTED | REJECTED
    status          VARCHAR(20)    NOT NULL,
    valid_until     DATE           NULL,
    notes           VARCHAR(1000)  NULL,
    subtotal        DECIMAL(15, 2) NOT NULL,
    tax_total       DECIMAL(15, 2) NOT NULL,
    total           DECIMAL(15, 2) NOT NULL,
    submitted_by    BIGINT         NOT NULL,
    created_at      TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version         BIGINT         NOT NULL DEFAULT 0,
    UNIQUE KEY uk_quotation (rfq_id, supplier_org_id),
    CONSTRAINT fk_quotation_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs (id),
    CONSTRAINT fk_quotation_org FOREIGN KEY (supplier_org_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quotation_lines (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id BIGINT         NOT NULL,
    rfq_line_id  BIGINT         NOT NULL,
    unit_price   DECIMAL(15, 2) NOT NULL,
    tax_percent  DECIMAL(5, 2)  NOT NULL,
    CONSTRAINT fk_qline_quotation FOREIGN KEY (quotation_id) REFERENCES quotations (id) ON DELETE CASCADE,
    CONSTRAINT fk_qline_rfq_line FOREIGN KEY (rfq_line_id) REFERENCES rfq_lines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A snapshot of the accepted quotation: later edits to anything upstream do not change the order.
CREATE TABLE purchase_orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    number          VARCHAR(20)    NULL,
    rfq_id          BIGINT         NOT NULL,
    quotation_id    BIGINT         NOT NULL,
    buyer_org_id    BIGINT         NOT NULL,
    supplier_org_id BIGINT         NOT NULL,
    -- PENDING_APPROVAL | ISSUED | ACKNOWLEDGED | PARTIALLY_RECEIVED | RECEIVED | CLOSED | CANCELLED
    status          VARCHAR(30)    NOT NULL,
    subtotal        DECIMAL(15, 2) NOT NULL,
    tax_total       DECIMAL(15, 2) NOT NULL,
    total           DECIMAL(15, 2) NOT NULL,
    delivery_site   VARCHAR(300)   NULL,
    reference       VARCHAR(120)   NULL,
    created_by      BIGINT         NOT NULL,
    approved_by     BIGINT         NULL,
    approved_at     TIMESTAMP(3)   NULL,
    acknowledged_by BIGINT         NULL,
    acknowledged_at TIMESTAMP(3)   NULL,
    cancel_reason   VARCHAR(500)   NULL,
    created_at      TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version         BIGINT         NOT NULL DEFAULT 0,
    UNIQUE KEY uk_po_number (number),
    UNIQUE KEY uk_po_quotation (quotation_id),
    INDEX idx_po_buyer (buyer_org_id),
    INDEX idx_po_supplier (supplier_org_id),
    CONSTRAINT fk_po_rfq FOREIGN KEY (rfq_id) REFERENCES rfqs (id),
    CONSTRAINT fk_po_quotation FOREIGN KEY (quotation_id) REFERENCES quotations (id),
    CONSTRAINT fk_po_buyer FOREIGN KEY (buyer_org_id) REFERENCES organizations (id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_org_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE po_lines (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL,
    line_no           INT            NOT NULL,
    description       VARCHAR(300)   NOT NULL,
    quantity          DECIMAL(15, 3) NOT NULL,
    uom               VARCHAR(20)    NOT NULL,
    unit_price        DECIMAL(15, 2) NOT NULL,
    tax_percent       DECIMAL(5, 2)  NOT NULL,
    CONSTRAINT fk_poline_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE goods_receipts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    number            VARCHAR(20)   NULL,
    purchase_order_id BIGINT        NOT NULL,
    notes             VARCHAR(1000) NULL,
    received_by       BIGINT        NOT NULL,
    created_at        TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_grn_number (number),
    CONSTRAINT fk_grn_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE goods_receipt_lines (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    goods_receipt_id BIGINT         NOT NULL,
    po_line_id       BIGINT         NOT NULL,
    received_qty     DECIMAL(15, 3) NOT NULL,
    rejected_qty     DECIMAL(15, 3) NOT NULL,
    CONSTRAINT fk_grnline_grn FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_grnline_poline FOREIGN KEY (po_line_id) REFERENCES po_lines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE supplier_invoices (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL,
    -- The supplier's own invoice number, unique per supplier.
    invoice_number    VARCHAR(40)    NOT NULL,
    supplier_org_id   BIGINT         NOT NULL,
    -- MATCHED | EXCEPTION | APPROVED | REJECTED
    status            VARCHAR(20)    NOT NULL,
    subtotal          DECIMAL(15, 2) NOT NULL,
    tax_total         DECIMAL(15, 2) NOT NULL,
    total             DECIMAL(15, 2) NOT NULL,
    -- Why the three-way match failed, one reason per line.
    match_issues      TEXT           NULL,
    submitted_by      BIGINT         NOT NULL,
    decided_by        BIGINT         NULL,
    decided_at        TIMESTAMP(3)   NULL,
    decision_note     VARCHAR(500)   NULL,
    created_at        TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version           BIGINT         NOT NULL DEFAULT 0,
    UNIQUE KEY uk_invoice_number (supplier_org_id, invoice_number),
    INDEX idx_invoice_po (purchase_order_id),
    CONSTRAINT fk_invoice_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE supplier_invoice_lines (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id  BIGINT         NOT NULL,
    po_line_id  BIGINT         NOT NULL,
    quantity    DECIMAL(15, 3) NOT NULL,
    unit_price  DECIMAL(15, 2) NOT NULL,
    tax_percent DECIMAL(5, 2)  NOT NULL,
    CONSTRAINT fk_invline_invoice FOREIGN KEY (invoice_id) REFERENCES supplier_invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_invline_poline FOREIGN KEY (po_line_id) REFERENCES po_lines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
