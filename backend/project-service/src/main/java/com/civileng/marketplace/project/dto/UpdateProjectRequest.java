package com.civileng.marketplace.project.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every field is optional — a null means "leave as is", so a caller editing only the budget
 * ceiling cannot accidentally blank the address.
 */
@Data
public class UpdateProjectRequest {

    private String name;

    private String description;

    private String addressLine;

    private String city;

    private String state;

    private String pincode;

    @DecimalMin(value = "0.0", inclusive = false, message = "Budget ceiling must be positive")
    private BigDecimal budgetCeiling;

    private String costCentre;

    private LocalDate startDate;

    private LocalDate endDate;

    /** Recorded in the audit trail when the edit changes scope or budget (FR-08). */
    private String reason;
}
