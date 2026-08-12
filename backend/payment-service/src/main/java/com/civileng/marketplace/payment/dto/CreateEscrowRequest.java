package com.civileng.marketplace.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateEscrowRequest {

    @NotNull(message = "Booking id is required")
    private Long bookingId;

    /** Optional — a hold can be scoped to a project milestone or to the booking alone. */
    private Long projectId;

    private Long milestoneId;

    @NotNull(message = "Payee id is required")
    private Long payeeId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    private String notes;
}
