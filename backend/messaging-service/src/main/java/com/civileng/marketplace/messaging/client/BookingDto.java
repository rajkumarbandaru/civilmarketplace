package com.civileng.marketplace.messaging.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private Long customerId;
    private Long workerId;
    private String status;

    public boolean involves(Long userId) {
        return userId != null && (userId.equals(customerId) || userId.equals(workerId));
    }
}
