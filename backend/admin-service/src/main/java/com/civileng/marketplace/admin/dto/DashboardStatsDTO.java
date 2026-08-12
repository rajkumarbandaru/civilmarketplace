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
public class DashboardStatsDTO {

    private long totalUsers;
    private long activeBookings;
    private double monthlyRevenue;
    private long pendingActions;
    private String userGrowth;
    private String bookingGrowth;
    private String revenueGrowth;
    private String pendingActionsChange;

    private List<RecentActivityDTO> recentActivity;
    private List<CityStatDTO> topCities;
    private PlatformOverviewDTO platformOverview;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityDTO {
        private String action;
        private String user;
        private String time;
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CityStatDTO {
        private String name;
        private long users;
        private int percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformOverviewDTO {
        private long totalEngineers;
        private long activeProjects;
        private long pendingVerifications;
        private long disputes;
        private long cancelledBookings;
        private double averageRating;
    }
}
