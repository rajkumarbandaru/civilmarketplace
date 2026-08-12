package com.civileng.marketplace.project.dto;

import com.civileng.marketplace.project.model.Milestone;
import com.civileng.marketplace.project.model.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * ENT·01 FR-04's budget-vs-actual rollup, plus FR-07's percent-complete.
 *
 * <p>{@code bookingDataAvailable} is false when booking-service could not be reached. Callers
 * must not read the zeroed spend figures as "nothing spent" — the alternative, failing the whole
 * summary, would take a Company's dashboard down during exactly the site dispute it is consulted
 * for.
 *
 * <p>Escrow figures come from payment-service; invoices are still absent, since payment-service
 * has no invoice entity yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummary {

    private Project project;

    private List<Milestone> milestones;

    private BigDecimal budgetCeiling;

    /** Sum of milestone allocations; may exceed the ceiling — that is a warning, not an error. */
    private BigDecimal allocatedBudget;

    private BigDecimal actualSpend;

    /** Ceiling minus actual spend; null when no ceiling is set. */
    private BigDecimal remainingBudget;

    private boolean overAllocated;

    private boolean overBudget;

    private int milestoneCount;

    private int completedMilestoneCount;

    /** 0–100, from milestone completion. Null when the project has no milestones. */
    private Integer percentComplete;

    private int bookingCount;

    private int activeBookingCount;

    private boolean bookingDataAvailable;

    /** Funded escrow that has not been paid out — committed, not yet spent. */
    private BigDecimal escrowHeld;

    /** Escrow already paid out to payees, net of platform commission. */
    private BigDecimal escrowReleased;

    private int escrowHoldCount;

    private int disputedEscrowCount;

    /** False when payment-service could not be reached; the escrow figures are then meaningless. */
    private boolean escrowDataAvailable;
}
