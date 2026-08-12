package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.dto.ApiResponse;
import com.civileng.marketplace.admin.dto.DashboardStatsDTO;
import com.civileng.marketplace.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Admin dashboard statistics and overview APIs")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard statistics")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getDashboard() {
        DashboardStatsDTO stats = adminDashboardService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get dashboard summary stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        DashboardStatsDTO stats = adminDashboardService.getDashboardStats();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "totalUsers", stats.getTotalUsers(),
                        "activeBookings", stats.getActiveBookings(),
                        "monthlyRevenue", stats.getMonthlyRevenue(),
                        "pendingActions", stats.getPendingActions(),
                        "userGrowth", stats.getUserGrowth(),
                        "bookingGrowth", stats.getBookingGrowth(),
                        "revenueGrowth", stats.getRevenueGrowth()
                )
        ));
    }

    @GetMapping("/dashboard/activity")
    @Operation(summary = "Get recent activity feed")
    public ResponseEntity<ApiResponse<?>> getRecentActivity() {
        DashboardStatsDTO stats = adminDashboardService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok(stats.getRecentActivity()));
    }

    @GetMapping("/dashboard/cities")
    @Operation(summary = "Get top cities data")
    public ResponseEntity<ApiResponse<?>> getTopCities() {
        DashboardStatsDTO stats = adminDashboardService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok(stats.getTopCities()));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", "admin-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
