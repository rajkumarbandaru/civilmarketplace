package com.civileng.marketplace.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusChangeRequest {

    @NotNull(message = "Target status is required")
    private String status;

    private String reason;
}
