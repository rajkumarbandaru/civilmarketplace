-- Support / Helpdesk ticketing — SRS OPS·02. No CEP implementation to port (it was a stub there
-- too); ticket replies reuse messaging-service's thread shape rather than a new model.
--
-- No foreign keys to users: identity lives in another service's schema, so cross-entity integrity
-- is enforced in the application rather than by the database.

CREATE TABLE support_tickets (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT       NOT NULL,
    -- NULL until a staff member picks it up.
    assignee_id BIGINT,
    subject     VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    category    VARCHAR(50),
    -- LOW | MEDIUM | HIGH | URGENT
    priority    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    -- OPEN | IN_PROGRESS | RESOLVED | CLOSED
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    closed_at   TIMESTAMP    NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ticket_reporter (reporter_id),
    INDEX idx_ticket_assignee (assignee_id),
    INDEX idx_ticket_status (status)
) ENGINE = InnoDB;

CREATE TABLE ticket_messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL,
    sender_id  BIGINT       NOT NULL,
    body       VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_message_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id),
    INDEX idx_ticket_message_ticket (ticket_id, id)
) ENGINE = InnoDB;
