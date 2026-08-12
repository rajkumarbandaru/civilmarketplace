package com.civileng.marketplace.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

/**
 * The slice of booking-service's Booking this service needs. Ignores unknown fields so a
 * booking-service change does not break the rollup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingDto {

    /** A booking in any of these has finished moving — nothing further will be spent on it. */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "CANCELLED", "REFUNDED");

    private Long id;
    private Long projectId;
    private Long milestoneId;
    private Long customerId;
    private Long workerId;
    private String status;
    private String serviceName;
    private BigDecimal estimatedCost;
    private BigDecimal finalCost;
    private BigDecimal totalAmount;

    public boolean isTerminal() {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * What this booking actually costs the project: the agreed total where one exists, else the
     * final cost, else the estimate. Cancelled and refunded bookings contribute nothing.
     */
    public BigDecimal spendContribution() {
        if (status == null || "CANCELLED".equals(status) || "REFUNDED".equals(status)) {
            return BigDecimal.ZERO;
        }
        if (totalAmount != null) return totalAmount;
        if (finalCost != null) return finalCost;
        return estimatedCost != null ? estimatedCost : BigDecimal.ZERO;
    }
}
