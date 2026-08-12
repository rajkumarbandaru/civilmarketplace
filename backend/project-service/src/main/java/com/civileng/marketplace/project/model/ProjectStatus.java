package com.civileng.marketplace.project.model;

/** SRS ENT·01 FR-05. Every transition between these is recorded in project_status_history. */
public enum ProjectStatus {
    DRAFT,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    /** Terminal states cannot transition anywhere else. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
