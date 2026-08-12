package com.civileng.marketplace.project.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    private String name;

    private String description;

    @NotNull(message = "Project type is required")
    private String projectType;

    private String addressLine;

    private String city;

    private String state;

    private String pincode;

    @DecimalMin(value = "0.0", inclusive = false, message = "Budget ceiling must be positive")
    private BigDecimal budgetCeiling;

    private String costCentre;

    private LocalDate startDate;

    private LocalDate endDate;
}
