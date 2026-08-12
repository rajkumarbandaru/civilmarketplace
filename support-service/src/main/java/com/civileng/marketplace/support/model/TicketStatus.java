package com.civileng.marketplace.support.model;

public enum TicketStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED;

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}
