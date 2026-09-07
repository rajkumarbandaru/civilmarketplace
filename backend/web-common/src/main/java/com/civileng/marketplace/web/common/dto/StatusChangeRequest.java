package com.civileng.marketplace.web.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A request to move something to a new status, with an optional reason.
 *
 * <p>The same three lines existed in project-service and support-service. They differed only in
 * validation — {@code @NotNull} against {@code @NotBlank} — where {@code @NotBlank} is strictly
 * stronger on a {@code String}: it rejects {@code ""} and {@code "   "}, which {@code @NotNull}
 * accepts and which no status transition can do anything with. That is the one kept.
 *
 * <p>Deliberately a {@code String} rather than a shared enum: the valid statuses are per-service
 * (a project's DRAFT→ACTIVE→COMPLETED, a ticket's OPEN→IN_PROGRESS→RESOLVED) and each service
 * validates the value against its own state machine. A shared enum would be the union of two
 * unrelated vocabularies and would let a caller name a ticket status on a project.
 */
@Data
public class StatusChangeRequest {

    @NotBlank(message = "Target status is required")
    private String status;

    private String reason;
}
