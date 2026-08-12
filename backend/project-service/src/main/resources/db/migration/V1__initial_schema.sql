-- Project management — SRS ENT·01. The parent record a Booking, Invoice and (once CP·06's escrow
-- lands) an EscrowHold hang off, so a Company can see one budget-vs-actual view across a dozen
-- separate bookings.
--
-- No foreign keys to bookings or users: those live in other services' schemas, so cross-entity
-- integrity is enforced in the application (via Feign) rather than by the database.

CREATE TABLE projects (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT       NOT NULL,
    name           VARCHAR(200) NOT NULL,
    description    VARCHAR(2000),
    -- NEW_BUILD | RENOVATION | INTERIOR | SINGLE_TRADE
    project_type   VARCHAR(30)  NOT NULL,
    -- DRAFT | ACTIVE | ON_HOLD | COMPLETED | CANCELLED
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    address_line   VARCHAR(500),
    city           VARCHAR(100),
    state          VARCHAR(100),
    pincode        VARCHAR(10),
    budget_ceiling DECIMAL(15,2),
    -- ENT·01 FR-10: consumed by Reports & analytics (ENT·05) once that module exists.
    cost_centre    VARCHAR(100),
    start_date     DATE,
    end_date       DATE,
    -- Soft delete: a deleted project must not orphan the bookings that reference it.
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP    NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_owner (owner_id),
    INDEX idx_project_status (status)
) ENGINE = InnoDB;

CREATE TABLE milestones (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(2000),
    target_date       DATE,
    budget_allocation DECIMAL(15,2),
    -- PENDING | IN_PROGRESS | COMPLETED | CANCELLED
    completion_state  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    completed_at      TIMESTAMP    NULL,
    sort_order        INT          NOT NULL DEFAULT 0,
    -- Soft delete per ENT·01's edge cases: a milestone referenced by a completed booking is
    -- never physically removed, or that booking loses its historical reference.
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_milestone_project FOREIGN KEY (project_id) REFERENCES projects (id),
    INDEX idx_milestone_project (project_id)
) ENGINE = InnoDB;

-- Object-storage reference only (ENT·01 FR-06) — never inline binary, same rule as KycDocument.
CREATE TABLE project_documents (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    file_ref    VARCHAR(500) NOT NULL,
    doc_type    VARCHAR(50)  NOT NULL,
    file_name   VARCHAR(255),
    uploaded_by BIGINT       NOT NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_project FOREIGN KEY (project_id) REFERENCES projects (id),
    INDEX idx_document_project (project_id)
) ENGINE = InnoDB;

-- FR-05: every status transition is logged with actor and timestamp. Append-only by convention;
-- nothing in the service updates or deletes a row here.
CREATE TABLE project_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    actor_id    BIGINT,
    reason      VARCHAR(500),
    changed_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_project FOREIGN KEY (project_id) REFERENCES projects (id),
    INDEX idx_history_project (project_id)
) ENGINE = InnoDB;
