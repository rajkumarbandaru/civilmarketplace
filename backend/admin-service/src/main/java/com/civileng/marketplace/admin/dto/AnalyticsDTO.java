package com.civileng.marketplace.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDTO {

    private List<GrowthMetricDTO> growthMetrics;
    private List<MonthlyTrendDTO> monthlyTrend;
    private List<CategoryPerformanceDTO> topCategories;
    private List<CityPerformanceDTO> cityPerformance;
    private UserGrowthDTO userGrowth;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthMetricDTO {
        private String label;
        private String value;
        private String trend;
        private String icon;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrendDTO {
        private String month;
        private long users;
        private long bookings;
        private double revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryPerformanceDTO {
        private String name;
        private long bookings;
        private String growth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CityPerformanceDTO {
        private String city;
        private String users;
        private String bookings;
        private String revenue;
        private String growth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserGrowthDTO {
        private long totalUsers;
        private double averageMonthlyGrowth;
    }
}
