package com.civileng.marketplace.web.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

/**
 * The slice of booking-service's {@code Booking} that other services read.
 *
 * <p>One read-model where there were three — messaging-, review- and project-service each kept a
 * partial copy of the same upstream contract, with overlapping fields and the same
 * {@code involves}/{@code isCompleted} logic written twice. Three partial views of one contract is
 * the shape that drifts: a change in booking-service had to be found in each of them, and nothing
 * made it obvious they were the same thing.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is what makes one shared class safe here.
 * A service reading this gets nulls for fields its endpoint does not return, rather than a
 * deserialisation failure — so carrying the union of fields costs the narrower callers nothing.
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

    /** Whether this user is one of the two parties — the check both messaging and reviews gate on. */
    public boolean involves(Long userId) {
        return userId != null && (userId.equals(customerId) || userId.equals(workerId));
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isTerminal() {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    /** The other party, or null if this user is neither. */
    public Long counterpartyOf(Long userId) {
        if (userId == null) return null;
        if (userId.equals(customerId)) return workerId;
        if (userId.equals(workerId)) return customerId;
        return null;
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
