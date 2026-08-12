package com.civileng.marketplace.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MilestoneRequest {

    @NotBlank(message = "Milestone title is required")
    private String title;

    private String description;

    private LocalDate targetDate;

    private BigDecimal budgetAllocation;

    private Integer sortOrder;

    /**
     * Why the owner allocated past the project's budget ceiling. Over-allocation is a soft
     * warning, not a block (SRS business rule), but the override reason is logged.
     */
    private String overrideReason;
}
