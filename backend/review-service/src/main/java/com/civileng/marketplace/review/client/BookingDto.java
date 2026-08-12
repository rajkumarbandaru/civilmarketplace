package com.civileng.marketplace.review.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {

    private static final Set<String> COMPLETED_STATUSES = Set.of("COMPLETED");

    private Long id;
    private Long customerId;
    private Long workerId;
    private String status;

    public boolean isCompleted() {
        return status != null && COMPLETED_STATUSES.contains(status);
    }

    public boolean involves(Long userId) {
        return userId != null && (userId.equals(customerId) || userId.equals(workerId));
    }

    public Long counterpartyOf(Long userId) {
        if (userId == null) return null;
        if (userId.equals(customerId)) return workerId;
        if (userId.equals(workerId)) return customerId;
        return null;
    }
}
