-- Milestone escrow — SRS CP·06 FR-06/FR-09. Money the payer has committed to a milestone but
-- which the payee cannot draw until the payer confirms, an auto-release timer expires, or an
-- admin resolves a dispute.
--
-- The hold never *holds* money itself: funds sit with the PSP against the linked payment row, and
-- this table records the platform's claim over them. status is therefore derived from that
-- payment, never self-declared by the payer.

CREATE TABLE escrow_holds (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    escrow_code       VARCHAR(30)  NOT NULL UNIQUE,
    -- Owned by project-service; no FK, different schema.
    project_id        BIGINT       NULL,
    milestone_id      BIGINT       NULL,
    booking_id        BIGINT       NOT NULL,
    payer_id          BIGINT       NOT NULL,
    payee_id          BIGINT       NOT NULL,
    -- The payment that funds this hold. NULL only between hold creation and order creation.
    payment_id        BIGINT       NULL,
    amount            DECIMAL(12,2) NOT NULL,
    -- Commission is computed at release, per CP·06 FR-03, and frozen onto the row then so the
    -- arithmetic stays auditable to the paisa even if the rate changes afterwards.
    commission_amount DECIMAL(12,2) NULL,
    commission_rate   DECIMAL(5,2)  NULL,
    net_amount        DECIMAL(12,2) NULL,
    -- PENDING_FUNDING | HELD | RELEASED | REFUNDED | CANCELLED | DISPUTED
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING_FUNDING',
    -- FR-06's auto-release timer: once past this instant a HELD, undisputed hold releases itself.
    auto_release_at   TIMESTAMP    NULL,
    funded_at         TIMESTAMP    NULL,
    released_at       TIMESTAMP    NULL,
    released_by       BIGINT       NULL,
    refunded_at       TIMESTAMP    NULL,
    dispute_reason    VARCHAR(500) NULL,
    disputed_at       TIMESTAMP    NULL,
    notes             VARCHAR(500) NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_escrow_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    INDEX idx_escrow_booking (booking_id),
    INDEX idx_escrow_milestone (milestone_id),
    INDEX idx_escrow_project (project_id),
    INDEX idx_escrow_payee (payee_id),
    INDEX idx_escrow_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One open hold per milestone. A cancelled or refunded hold must not block a retry, so the
-- constraint is enforced in the service against non-terminal statuses rather than as a unique
-- index over milestone_id.

-- Wallet money that has arrived but is frozen behind an open dispute (FR-09). Kept separate from
-- `balance` so a payout check is a column read rather than a join across disputes.
ALTER TABLE wallets ADD COLUMN held_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00;
