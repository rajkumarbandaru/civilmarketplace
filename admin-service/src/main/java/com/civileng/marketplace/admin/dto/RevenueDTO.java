package com.civileng.marketplace.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueDTO {

    private RevenueSummaryDTO summary;
    private List<MonthlyRevenueDTO> monthlyRevenue;
    private RevenueBreakdownDTO breakdown;
    private List<TransactionDTO> recentTransactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueSummaryDTO {
        private BigDecimal totalRevenueMtd;
        private BigDecimal platformFees;
        private BigDecimal pendingPayouts;
        private BigDecimal refundsMtd;
        private String revenueChange;
        private String platformFeePercentage;
        private int pendingPayoutWorkers;
        private String refundChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyRevenueDTO {
        private String month;
        private BigDecimal revenue;
        private BigDecimal fees;
        private BigDecimal payouts;
        private BigDecimal profit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueBreakdownDTO {
        private List<BreakdownItemDTO> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownItemDTO {
        private String label;
        private BigDecimal value;
        private int percentage;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDTO {
        private String transactionId;
        private String bookingCode;
        private String customerName;
        private BigDecimal amount;
        private String type;
        private String status;
        private String date;
    }
}
