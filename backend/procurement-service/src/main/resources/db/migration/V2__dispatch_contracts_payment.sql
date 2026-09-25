-- ============================================================================
-- Phase 5 extras: dispatch with e-way bill, price lists and contracts, payment terms and invoice
-- payment, and organizations created from existing accounts (the party-model migration).
-- ============================================================================

-- What the supplier sent and how. An e-way bill is required by GST law for a consignment of goods
-- worth more than ₹50,000; the rule is enforced by the service, the number stored here.
CREATE TABLE dispatches (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    number            VARCHAR(20)    NULL,
    purchase_order_id BIGINT         NOT NULL,
    vehicle_number    VARCHAR(20)    NOT NULL,
    transporter       VARCHAR(120)   NULL,
    eway_bill_number  VARCHAR(12)    NULL,
    consignment_value DECIMAL(15, 2) NOT NULL,
    dispatched_by     BIGINT         NOT NULL,
    created_at        TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_dispatch_number (number),
    INDEX idx_dispatch_po (purchase_order_id),
    CONSTRAINT fk_dispatch_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dispatch_lines (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispatch_id BIGINT         NOT NULL,
    po_line_id  BIGINT         NOT NULL,
    quantity    DECIMAL(15, 3) NOT NULL,
    CONSTRAINT fk_dline_dispatch FOREIGN KEY (dispatch_id) REFERENCES dispatches (id) ON DELETE CASCADE,
    CONSTRAINT fk_dline_poline FOREIGN KEY (po_line_id) REFERENCES po_lines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A receipt may say which delivery it is for; each delivery is received once.
ALTER TABLE goods_receipts
    ADD COLUMN dispatch_id BIGINT NULL AFTER purchase_order_id,
    ADD UNIQUE KEY uk_grn_dispatch (dispatch_id),
    ADD CONSTRAINT fk_grn_dispatch FOREIGN KEY (dispatch_id) REFERENCES dispatches (id);

-- A supplier's prices. With no buyer it is the supplier's standard catalogue, live as soon as it
-- is saved. With a buyer it is a contract (the CONTRACTED relationship, architecture 07 §2.1):
-- proposed by the supplier, binding once the buyer accepts — contract rates cap what the supplier
-- may quote that buyer, and its payment terms and credit limit govern the buyer's orders.
CREATE TABLE price_lists (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_org_id    BIGINT         NOT NULL,
    buyer_org_id       BIGINT         NULL,
    name               VARCHAR(120)   NOT NULL,
    -- ACTIVE | PROPOSED | DECLINED | TERMINATED
    status             VARCHAR(20)    NOT NULL,
    -- Net N: an approved invoice is due N days later. 0 = on approval.
    payment_terms_days INT            NOT NULL DEFAULT 0,
    -- Open orders the buyer may have with this supplier at once. NULL = no limit.
    credit_limit       DECIMAL(15, 2) NULL,
    valid_from         DATE           NULL,
    valid_until        DATE           NULL,
    created_by         BIGINT         NOT NULL,
    decided_by         BIGINT         NULL,
    decided_at         TIMESTAMP(3)   NULL,
    created_at         TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version            BIGINT         NOT NULL DEFAULT 0,
    INDEX idx_pricelist_pair (supplier_org_id, buyer_org_id, status),
    CONSTRAINT fk_pricelist_supplier FOREIGN KEY (supplier_org_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_pricelist_buyer FOREIGN KEY (buyer_org_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE price_list_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    price_list_id BIGINT         NOT NULL,
    description   VARCHAR(300)   NOT NULL,
    uom           VARCHAR(20)    NOT NULL,
    unit_price    DECIMAL(15, 2) NOT NULL,
    tax_percent   DECIMAL(5, 2)  NOT NULL,
    CONSTRAINT fk_plitem_list FOREIGN KEY (price_list_id) REFERENCES price_lists (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The terms an order was placed under, snapshotted like its prices.
ALTER TABLE purchase_orders
    ADD COLUMN payment_terms_days INT NOT NULL DEFAULT 0 AFTER total,
    ADD COLUMN contract_id BIGINT NULL AFTER payment_terms_days;

-- Due on approval + the order's terms; PAID once payment-service confirms the money moved.
ALTER TABLE supplier_invoices
    ADD COLUMN due_date          DATE         NULL AFTER decision_note,
    ADD COLUMN payment_id        BIGINT       NULL AFTER due_date,
    ADD COLUMN payment_reference VARCHAR(100) NULL AFTER payment_id,
    ADD COLUMN paid_at           TIMESTAMP(3) NULL AFTER payment_reference;

-- An organization made from an existing supplier's or contractor's account: at most one each,
-- which is what makes the migration safe to run again.
ALTER TABLE organizations
    ADD COLUMN source_user_id BIGINT NULL AFTER approval_threshold,
    ADD UNIQUE KEY uk_org_source_user (source_user_id);
