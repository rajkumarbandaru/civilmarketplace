package com.civileng.marketplace.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {

    private Long id;
    private String bookingCode;
    private String customerName;
    private Long customerId;
    private String workerName;
    private Long workerId;
    private String serviceName;
    private String serviceCategory;
    private String status;
    private BigDecimal amount;
    private String city;
    private LocalDateTime scheduledDate;
    private LocalDateTime createdAt;
    private String paymentStatus;
    private String paymentMethod;
    private String description;
    private String cancellationReason;
    private LocalDateTime completedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBookingStatusRequest {
        private String status;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteBookingRequest {
        private BigDecimal finalCost;
    }
}
